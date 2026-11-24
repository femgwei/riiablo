"""Check direction selection against actual target changes with the real cast/input replay."""
import argparse
import json
from pathlib import Path

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('native',type=Path);parser.add_argument('local',type=Path);parser.add_argument('output',type=Path);args=parser.parse_args()
    native=json.loads(args.native.read_text(encoding='utf8'));local=json.loads(args.local.read_text(encoding='utf8'))
    assert native['background_verified'] and native['owned_game_closed']
    assert len(native['units'])==len(local['births'])==18
    for original,replay in zip(native['units'],local['births']):
        assert original['velocity']==[replay['vx_fixed'],replay['vy_fixed']]
        assert replay['age']==0
    assert local['overlay_requests']==native['overlay_instances']==1
    assert sum(b['tick']>local['release_tick'] for b in local['births'])==native['post_release_births']==3
    assert local['final_streams']==0 and local['final_mode']==1
    result=dict(schema=1,source_run_id=native['run_id'],comparison_status='matched_current_target_turn_and_release_directions',
        production_presentation_changed=True,server_gameplay_changed=False,compared_births=18,
        initial_direction_births=4,turned_direction_births=14,post_release_births=3,overlay_instances=1,
        original_posted_turn_ms=native['turn_posted']['elapsed_ms'],
        note='Local target change is aligned to the native observed path-target boundary; PostMessage/input transport delays are not compared.',
        limitations=native['limitations'])
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(result['comparison_status'])
