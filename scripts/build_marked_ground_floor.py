"""Rebuild the routing draft from the user-marked plan; distances remain estimates."""
from pathlib import Path
import csv
from html import escape
import json
import math
import shutil
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'python'))
from rain_offline.validate_graph import ROOM_FIELDS, EDGE_FIELDS, validate
from rain_offline.build_floor_pack import build

OUT = ROOT / 'data/ground-floor'
FLOOR_NAME = 'Ground floor - marked draft'
NOTE = 'USER MARKUP: drawing-derived position; estimated metres; sign condition and field access unverified'


def generate():
    drawing = json.loads((OUT / 'marked-annotations.json').read_text(encoding='utf-8'))
    nodes, edges, pixels, inventory = [], [], {}, []
    corridors = {}
    # Preserve the old nominal scale across the resized annotation. This is not a survey.
    sx, sy = .07 * 1673 / drawing['image_width'], .10 * 940 / drawing['image_height']

    def node(key, label, point, kind='junction', aliases='', note=NOTE):
        x, y = point
        pixels[key] = point
        nodes.append(dict(zip(ROOM_FIELDS, [key, kind, label, '', aliases,
            round((x-900)*sx, 2), round((790-y)*sy, 2), '', 'missing', 0, note])))
        return key

    def corridor_node(point):
        point = tuple(point)
        if point not in corridors:
            key = f'P{len(corridors):03}'
            corridors[point] = node(key, '', point)
        return corridors[point]

    def edge(a, b, corridor, note=NOTE):
        ax, ay = pixels[a]; bx, by = pixels[b]
        dx, dy = (bx-ax)*sx, (ay-by)*sy
        if a == b:
            raise ValueError('Duplicate points would create a zero-length route')
        edges.append(dict(zip(EDGE_FIELDS, [f'E{len(edges)+1:03}', a, b,
            round(math.hypot(dx,dy),2), round(math.degrees(math.atan2(dx,dy))%360,2),
            corridor, 'true', 'false', note])))

    for corridor in drawing['corridors']:
        keys = [corridor_node(p) for p in corridor['points']]
        for a,b in zip(keys,keys[1:]):
            edge(a,b,corridor['id'])

    for door in drawing['doors']:
        parent = corridors[tuple(door['approach'])]
        node(door['id'], door['label'], door['pixel'], door.get('type','door'), door.get('aliases',''))
        edge(parent,door['id'],'DOOR_APPROACH')
        inventory.append(dict(node_id=door['id'],label=door['label'],door_position_source='user_red_mark',
            door_verified_on_site=False,sign_verified=False,access_verified_on_site=False))

    for stairs in drawing['stairs']:
        parent = corridors[tuple(stairs['approach'])]
        note = NOTE + '; stair access only: route stops before flight; upper floor not mapped'
        node(stairs['id'], stairs['label'], stairs['pixel'],'stairs', note=note)
        edge(parent,stairs['id'],'STAIR_ACCESS',note)
        inventory.append(dict(node_id=stairs['id'],label=stairs['label'],stair_location_source='user_yellow_highlight',
            routing_status='level approach to stair access only; no stair flight or floor transition'))

    # A lobby reference point, not an inferred external entrance or a lift.
    node('ENT','Main lobby',[900,735],'entrance','Lobby|Main lobby entrance',NOTE+'; interior lobby reference point')
    edge(corridors[(900,701)],'ENT','LOBBY_REFERENCE')
    for label in drawing['unrouted_rooms']:
        inventory.append(dict(label=label,routing_status='excluded pending marked doorway/access clarification'))
    for mark in drawing['unresolved_marks']:
        inventory.append(dict(mark_id=mark['id'],pixel=mark['pixel'],routing_status=mark['note']))

    for filename,fields,rows in [('rooms.csv',ROOM_FIELDS,nodes),('edges.csv',EDGE_FIELDS,edges)]:
        with (OUT/filename).open('w',newline='',encoding='utf-8') as stream:
            writer=csv.DictWriter(stream,fieldnames=fields);writer.writeheader();writer.writerows(rows)
    errors,_,_,_=validate(OUT/'rooms.csv',OUT/'edges.csv')
    if errors:
        raise ValueError(errors)
    pack=build(OUT/'rooms.csv',OUT/'edges.csv',FLOOR_NAME,'plan_derived_floor')
    text=json.dumps(pack,ensure_ascii=False,indent=2)
    (ROOT/'output').mkdir(exist_ok=True)
    (ROOT/'output/ground-floor-pack.json').write_text(text,encoding='utf-8')
    (ROOT/'android/app/src/main/assets/ground-floor.json').write_text(text,encoding='utf-8')
    (OUT/'survey-checklist.json').write_text(json.dumps(inventory,indent=2),encoding='utf-8')
    render_review(drawing,nodes,edges,pixels)
    print(f'Marked ground floor: {len(nodes)} nodes, {len(edges)} segments, {len(drawing["doors"])} door approaches, {len(drawing["stairs"])} stair accesses.')


def render_review(drawing,nodes,edges,pixels):
    names={n['node_id']:n['room_name'] or n['node_id'] for n in nodes}
    lines=''.join(f'<line x1="{pixels[e["from_node"]][0]}" y1="{pixels[e["from_node"]][1]}" x2="{pixels[e["to_node"]][0]}" y2="{pixels[e["to_node"]][1]}"/>' for e in edges)
    dots=''.join(f'<circle cx="{x}" cy="{y}" r="4"><title>{escape(key+": "+names[key])}</title></circle>' for key,(x,y) in pixels.items())
    pending=''.join(f'<circle cx="{m["pixel"][0]}" cy="{m["pixel"][1]}" r="12"><title>{escape(m["id"]+": "+m["note"])}</title></circle>' for m in drawing['unresolved_marks'])
    stair_boxes=''.join(f'<rect x="{s["bounds"][0]}" y="{s["bounds"][1]}" width="{s["bounds"][2]-s["bounds"][0]}" height="{s["bounds"][3]-s["bounds"][1]}"><title>{escape(s["label"])}</title></rect>' for s in drawing['stairs'])
    issues=''.join(f'<li><b>{escape(m["id"])}</b>: {escape(m["note"])}</li>' for m in drawing['unresolved_marks'])
    html=f'''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>RAIN marked floor review</title>
<style>body{{font:16px system-ui;margin:24px;color:#173f35}}svg{{width:100%;height:auto}}#routes line{{stroke:#08bed1;stroke-width:3}}#routes circle{{fill:#071a52;stroke:white;stroke-width:1}}#pending circle{{fill:none;stroke:#d400ce;stroke-width:4}}#stairs rect{{fill:#ffd50033;stroke:#c89900;stroke-width:3}}button{{padding:10px;margin:4px}}li{{margin:8px 0}}</style>
<h1>RAIN: marked ground-floor review</h1><p>Blue/red/green/yellow paint is the supplied annotation. Cyan lines and dark dots show the current routing graph. Purple rings identify door associations awaiting clarification. Yellow outlines mark stairs; routes stop at their access points.</p>
<p>Printed units are confirmed as metres. The central lobby connection is confirmed level. Route lengths remain drawing estimates, and signs require field checks.</p>
<button onclick="toggle('routes')">Show/hide routes</button><button onclick="toggle('pending')">Show/hide questions</button><button onclick="switchPlan()">Original / marked plan</button>
<svg viewBox="0 0 1600 898" role="img" aria-label="Marked ground-floor plan with routing overlay"><image id="plan" href="../data/ground-floor/marked-plan.jpeg" width="1600" height="898"/><g id="routes">{lines}{dots}</g><g id="stairs">{stair_boxes}</g><g id="pending">{pending}</g></svg>
<h2>Doorway questions</h2><ul>{issues}</ul><p>Green opening connections and the west-end black cross await legend confirmation. Unmarked room entrances are not assumed. The courtyard remains visible on the plan; its routing connections await the opening legend.</p>
<script>function toggle(id){{let s=document.getElementById(id).style;s.display=s.display==='none'?'':'none'}}let original=false;function switchPlan(){{original=!original;document.getElementById('plan').setAttribute('href',original?'../data/ground-floor/source-plan.png':'../data/ground-floor/marked-plan.jpeg')}}</script></html>'''
    (ROOT/'docs/ground-floor-review.html').write_text(html,encoding='utf-8')
    portable=ROOT/'output/ground-floor-review';portable.mkdir(parents=True,exist_ok=True)
    (portable/'index.html').write_text(html.replace('../data/ground-floor/',''),encoding='utf-8')
    for name in ['marked-plan.jpeg','source-plan.png']:
        shutil.copy2(OUT/name,portable/name)


if __name__ == '__main__':
    generate()
