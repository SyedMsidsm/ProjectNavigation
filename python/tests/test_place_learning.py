import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from rain_offline.place_learning import FEATURES, MODEL_HASH, validate, train


def fixture():
    captures=[]
    for index,(split,count) in enumerate([('reference',2),('train',20),('validation',10),('test',10)]):
        for i in range(count):
            node='A' if i%2==0 else 'B'
            captures.append({'capture_id':f'{split}{i}','session_id':split,'split':split,'node_id':node,
                'embedding':[1.0,index+i/1000], 'candidates':[
                    {'node_id':n,'features':([.96,.95,1,1,1] if n==node else [.1,.2,1,1,0])} for n in ['A','B']]})
    return {'schema_version':1,'feature_names':FEATURES,'model_sha256':MODEL_HASH,'floor_digest':'a'*64,
            'data_source':'synthetic_floor','captures':captures}


class PlaceLearningTests(unittest.TestCase):
    def test_training_pipeline_on_explicit_synthetic_fixture(self):
        model,report=train(fixture())
        self.assertTrue(model['trained'])
        self.assertEqual(10,report['evaluation']['fusion']['captures'])
        self.assertGreater(report['evaluation']['fusion']['correct_accepted'],0)
        self.assertEqual('synthetic_floor',model['data_source'])

    def test_session_and_exact_capture_leakage_are_rejected(self):
        data=fixture();data['captures'][-1]['session_id']='train'
        with self.assertRaisesRegex(ValueError,'multiple splits'):validate(data)
        data=fixture();data['captures'][-1]['embedding']=data['captures'][0]['embedding']
        with self.assertRaisesRegex(ValueError,'identical embedding'):validate(data)

    def test_missing_labels_and_nonfinite_features_rejected(self):
        data=fixture();data['captures'][-1]['node_id']='C'
        with self.assertRaises(ValueError):validate(data)
        data=fixture();data['captures'][-1]['candidates'][0]['features'][0]=float('nan')
        with self.assertRaises(ValueError):validate(data)

    def test_small_dataset_cannot_produce_a_trained_deliverable(self):
        data=fixture();data['captures']=data['captures'][:4]
        with self.assertRaisesRegex(ValueError,'at least 20'):train(data)


if __name__=='__main__':unittest.main()
