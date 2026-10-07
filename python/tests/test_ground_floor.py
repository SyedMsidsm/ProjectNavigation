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
        self.assertEqual(74,len(nodes))
        self.assertTrue(all(n['sign_condition']=='missing' and not n['sign_text'] for n in nodes))
        pack=build(directory/'rooms.csv',directory/'edges.csv','Ground floor - plan draft','plan_derived_floor')
        self.assertEqual(pack,json.loads((ROOT/'android/app/src/main/assets/ground-floor.json').read_text()))

    def test_room_approaches_are_dead_ends_not_shortcuts(self):
        directory=ROOT/'data/ground-floor'
        _,_,nodes,edges=validate(directory/'rooms.csv',directory/'edges.csv')
        for node in nodes:
            if node['room_name']:
                self.assertEqual(1,sum(node['node_id'] in (e['from_node'],e['to_node']) for e in edges),node['node_id'])


if __name__=='__main__': unittest.main()
