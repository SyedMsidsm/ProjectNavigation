"""Reproducible draft transcription of the supplied ground-floor drawing.

Pixel positions describe corridor-facing approach points, NOT surveyed door centres.
Never treat this drawing-derived geometry as measured navigation ground truth.
"""
from pathlib import Path
import csv
import json
import math
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'python'))
from rain_offline.validate_graph import ROOM_FIELDS, EDGE_FIELDS, validate
from rain_offline.build_floor_pack import build

OUT = ROOT / 'data/ground-floor'
NOTE = 'PLAN DERIVED: assumed approach/door; estimated metres; sign condition and access unverified'


def generate():
    OUT.mkdir(parents=True, exist_ok=True)
    nodes, edges, pixels, inventory = [], [], {}, []

    def node(key, label, x, y, kind='junction', aliases=''):
        pixels[key] = (x, y)
        # The image is not uniformly scaled. These independent drawing scales are
        # nominal approximations, not a fit claiming measured corridor distances.
        nodes.append(dict(zip(ROOM_FIELDS, [key, kind, label, '', aliases,
            round((x-930)*0.07, 2), round((800-y)*0.10, 2), '', 'missing', 0, NOTE])))

    def edge(a, b, corridor):
        ax, ay = pixels[a]; bx, by = pixels[b]
        dx, dy = (bx-ax)*.07, (ay-by)*.10
        edges.append(dict(zip(EDGE_FIELDS, [f'E{len(edges)+1:03}', a, b,
            round(math.hypot(dx, dy), 2), round(math.degrees(math.atan2(dx, dy)) % 360, 2),
            corridor, 'true', 'false', NOTE])))

    def chain(prefix, points):
        keys = []
        for i, (x,y) in enumerate(points):
            key=f'{prefix}{i:02}'; node(key, '', x, y); keys.append(key)
        for a,b in zip(keys, keys[1:]): edge(a,b,prefix)
        return keys

    # Main cross-corridor, west spine, and library-side east spine.
    main = chain('C', [(180,590),(295,590),(380,590),(460,590),(525,590),
        (600,590),(700,590),(800,590),(940,590),(1040,590),(1125,590),
        (1220,590),(1340,590),(1450,590)])
    west = chain('W', [(525,550),(525,480),(525,430),(525,370),(525,240),(525,190),(525,20)])
    edge('C04','W00','WEST')
    north = chain('N', [(460,20),(390,20),(310,20)])
    edge('W06','N00','NORTH_WEST')
    chain('X', [(460,190),(415,190)])
    edge('W05','X00','EXAM_APPROACH')
    east = chain('R', [(1220,540),(1220,430),(1220,290),(1220,155),(1290,155),(1290,95)])
    edge('C11','R00','EAST')
    lobby = chain('L', [(940,690),(940,755),(850,755),(730,755),(650,755),(525,755)])
    edge('C08','L00','LOBBY')
    edge('L05','C04','WEST_LOBBY')
    node('ENT','Main lobby entrance',940,795,'entrance','Main entrance|Lobby entrance')
    edge('ENT','L01','LOBBY')

    def room(key,label,x,y,parent,dimensions='',aliases='',kind='door'):
        node(key,label,x,y,kind,aliases); edge(parent,key,'APPROACH')
        inventory.append({'node_id':key,'label':label,'printed_dimensions':dimensions,
            'door_verified':False,'sign_verified':False,'access_verified':False})

    room('A003','LH-A003',180,610,'C00','7 x 12.56','LH A003|A003')
    room('A002','LH-A002',295,610,'C01','7 x 11.8','LH A002|A002')
    room('A001','LH-A001',380,610,'C02','7 x 10.76','LH A001|A001')
    room('A004','LH-A004',600,610,'C05','9 x 10','LH A004|A004')
    room('A005','LH-A005',548,430,'W02','10 x 8.49','LH A005|A005')
    room('A006','LH-A006',460,38,'N00','8 x 10','LH A006|A006')
    room('A007','LH-A007',310,38,'N02','8.72 x 10','LH A007|A007')
    room('GRAPHICS','Engineering Graphics Lab',548,240,'W04','10 x 23.46','Graphics Lab')
    room('MATHS','Maths Department',548,550,'W00','10 x 3.0','Mathematics Department')
    room('EXAM_W','Examination Centre (west)',410,190,'X01','14 x 10','Examination Centre|Exam Centre')
    room('EXAM_S','Examination Centre (south)',503,370,'W03','18 x 10','Examination Centre|Exam Centre')
    room('PHY','PHY-HOD',503,480,'W01','5 x 3.86','Physics HOD')
    room('CHEM','CHEM-HOD',503,550,'W00','5 x 3.86','Chemistry HOD')
    room('TUT1','TUTL-1',295,567,'C01','5 x 8','Tutorial 1')
    room('TUT2','TUTL-2',380,567,'C02','5 x 8','Tutorial 2')
    room('GTOIL','G. Toilet',180,567,'C00',aliases='G.TOL|Gents toilet',kind='washroom')
    # L.TOL access through the narrow toilet vestibule requires an on-site check.
    node('TV','',220,590); edge('C00','TV','TOILET_APPROACH')
    node('TV2','',220,490); edge('TV','TV2','TOILET_APPROACH')
    room('LTOIL','L. Toilet',210,490,'TV2',aliases='L.TOL|Ladies toilet',kind='washroom')
    room('COMPUTER','Computer Lab',1203,290,'R02','13.68 x 14.0')
    room('CPROG','C Programming Lab',1203,430,'R01','13.68 x 7.0','C Lab')
    room('SERVER','Server',1203,155,'R03','10.68 x 2.86','Server Room')
    room('LIBRARY','Library',1242,430,'R01','13.28 x 25.44; area 330 SQMT')
    room('NCC','NCC Office',1323,155,'R04','10.0 x 2.86')
    room('PE','PE Department',1323,95,'R05','10.0 x 2.86','Physical Education Department')
    room('STUDY','Self Study Area',1450,558,'C13','6.00 x 5.85')
    room('STAFF','Staff Room',1450,610,'C13','5 x 7.26')
    room('GIRLS','Girls Lounge',1340,610,'C12','13.74 x 10.60')
    room('ELECTRICAL','Electrical Room',1220,610,'C11','3 x 4.5')
    room('OFFICE_EXAM','Office Examination',1125,610,'C10','7.90 x 11.90','Examination Office')
    room('WAIT','Waiting Area',1040,610,'C09','6.10 x 4.62')
    room('OFFICE','Office',1014,710,'L00','7.90 x 11.90','Administration Office')
    room('RECEPTION','Reception',800,735,'L02','6.96 x 4')
    room('CONF','Conference Room',730,778,'L03','11.66 x 6.00','CONF.RM')
    room('PRINCIPAL','Principal',650,778,'L04','9.5 x 5.83','Principal Office')
    # Internal/restricted approaches remain inventoried but excluded from routing.
    for label in ['Strong Room','VIP S. Room','Fees Counter','ELE. Room','Lift','Staircases','Courts and deck']:
        inventory.append({'label':label,'routing_status':'excluded: entrance/access or vertical connection not verified'})

    for filename, fields, rows in [('rooms.csv',ROOM_FIELDS,nodes),('edges.csv',EDGE_FIELDS,edges)]:
        with (OUT/filename).open('w',newline='',encoding='utf-8') as f:
            writer=csv.DictWriter(f,fieldnames=fields);writer.writeheader();writer.writerows(rows)
    errors,_,_,_=validate(OUT/'rooms.csv',OUT/'edges.csv')
    if errors: raise ValueError(errors)
    pack=build(OUT/'rooms.csv',OUT/'edges.csv','Ground floor - plan draft','plan_derived_floor')
    text=json.dumps(pack,ensure_ascii=False,indent=2)
    (ROOT/'output/ground-floor-pack.json').write_text(text,encoding='utf-8')
    (ROOT/'android/app/src/main/assets/ground-floor.json').write_text(text,encoding='utf-8')
    (OUT/'survey-checklist.json').write_text(json.dumps(inventory,indent=2),encoding='utf-8')
    # An image overlay lets a person spot topology assumptions before walking.
    lines=''.join(f'<line x1="{pixels[e["from_node"]][0]}" y1="{pixels[e["from_node"]][1]}" x2="{pixels[e["to_node"]][0]}" y2="{pixels[e["to_node"]][1]}"/>' for e in edges)
    dots=''.join(f'<circle cx="{x}" cy="{y}" r="4"><title>{key}</title></circle>' for key,(x,y) in pixels.items())
    html=f'''<!doctype html><meta charset="utf-8"><title>RAIN ground-floor route review</title>
<style>body{{font:16px system-ui;margin:24px;color:#173f35}}svg{{width:100%;height:auto}}line{{stroke:#007fda;stroke-width:3}}circle{{fill:#ff6b00}}button{{padding:10px}}</style>
<h1>Ground-floor draft: check the blue walking paths</h1><p>Drawing-derived approaches and distances, not surveyed routes. Orange dots are graph nodes. No path through the courtyard is assumed. Hover over a dot for its ID.</p>
<button onclick="document.getElementById('routes').style.display=document.getElementById('routes').style.display==='none'?'':'none'">Show / hide draft routes</button>
<svg viewBox="0 0 1673 940"><image href="../data/ground-floor/source-plan.png" width="1673" height="940"/><g id="routes">{lines}{dots}</g></svg>
<p>Excluded pending access confirmation: internal strong/VIP/fees/electrical rooms, lift, stairs and open courts. Building north, units, doorway positions, sign text, access and route lengths need a field check.</p>'''
    (ROOT/'docs/ground-floor-review.html').write_text(html,encoding='utf-8')
    print(f'Ground-floor draft: {len(nodes)} nodes, {len(edges)} segments, {len(inventory)} survey entries.')


if __name__ == '__main__':
    generate()
