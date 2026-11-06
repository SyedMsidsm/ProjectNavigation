import csv
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'python'))
from rain_offline.validate_graph import validate
from rain_offline.build_floor_pack import build


class GroundFloorTests(unittest.TestCase):
    def test_draft_is_valid_and_bundled_copy_matches(self):
        directory=ROOT/'data/ground-floor'
        errors,_,nodes,edges=validate(directory/'rooms.csv',directory/'edges.csv')
        self.assertEqual([],errors)
        self.assertGreater(len(nodes),50)
        self.assertTrue(all(n['sign_condition']=='missing' and not n['sign_text'] for n in nodes))
        pack=build(directory/'rooms.csv',directory/'edges.csv','Ground floor - marked draft','plan_derived_floor')
        self.assertEqual(pack,json.loads((ROOT/'android/app/src/main/assets/ground-floor.json').read_text()))

    def test_room_approaches_are_dead_ends_not_shortcuts(self):
        directory=ROOT/'data/ground-floor'
        _,_,nodes,edges=validate(directory/'rooms.csv',directory/'edges.csv')
        for node in nodes:
            if node['room_name']:
                self.assertEqual(1,sum(node['node_id'] in (e['from_node'],e['to_node']) for e in edges),node['node_id'])

    def test_marked_door_sides_and_multiple_entrances(self):
        directory=ROOT/'data/ground-floor'
        _,_,nodes,edges=validate(directory/'rooms.csv',directory/'edges.csv')
        by_id={n['node_id']:n for n in nodes}
        def approach(key):
            edge=next(e for e in edges if e['to_node']==key)
            return by_id[edge['from_node']]
        self.assertGreater(float(approach('A006')['x_m']),float(by_id['A006']['x_m']))
        self.assertLess(float(approach('LIBRARY')['y_m']),float(by_id['LIBRARY']['y_m']))
        self.assertIn('GRAPHICS_N',by_id)
        self.assertIn('COMPUTER_N',by_id)
        self.assertNotEqual(approach('GRAPHICS_N')['node_id'],approach('GRAPHICS')['node_id'])

    def test_stair_flights_are_never_crossed_by_routing_segments(self):
        directory=ROOT/'data/ground-floor'
        drawing=json.loads((directory/'marked-annotations.json').read_text())
        _,_,nodes,edges=validate(directory/'rooms.csv',directory/'edges.csv')
        self.assertTrue(drawing['lobby_middle_corridor_level_passage_confirmed'])
        self.assertEqual('metres',drawing['printed_dimension_unit'])
        self.assertEqual(3,sum(n['node_type']=='stairs' for n in nodes))
        self.assertTrue(all(e['has_stairs']=='false' for e in edges))
        sx,sy=.07*1673/1600,.10*940/898
        positions={n['node_id']:(float(n['x_m'])/sx+900,790-float(n['y_m'])/sy) for n in nodes}
        for edge in edges:
            a,b=positions[edge['from_node']],positions[edge['to_node']]
            for step in range(1,100):
                x=a[0]+(b[0]-a[0])*step/100
                y=a[1]+(b[1]-a[1])*step/100
                for stairs in drawing['stairs']:
                    left,top,right,bottom=stairs['bounds']
                    self.assertFalse(left+.15<x<right-.15 and top+.15<y<bottom-.15,(edge['edge_id'],stairs['id']))


if __name__=='__main__': unittest.main()
