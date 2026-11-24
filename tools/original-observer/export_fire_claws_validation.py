"""Archive bounded Fire Claws headless evidence without running the original game."""
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'tools/original-observer'
DATE = '2026-11-24'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def suite(path):
    tree = ET.parse(path).getroot()
    return {
        'suite': tree.get('name'),
        **{key: int(tree.get(key, 0)) for key in ('tests', 'failures', 'errors', 'skipped')},
        'cases': [case.get('name') for case in tree.findall('testcase')],
        'failure_details': [item.text for item in tree.findall('.//failure')],
        'stdout': '\n'.join(item.text or '' for item in tree.findall('system-out')),
        'xml': path.relative_to(ROOT).as_posix(), 'sha256': digest(path),
    }


def main():
    for module in ('core', 'server'):
        log = BASE / f'fire-claws-{module}-final-validation-{DATE}.log'
        if 'BUILD SUCCESSFUL' not in log.read_text(encoding='utf-8', errors='replace'):
            raise SystemExit(f'Final {module} build has not succeeded; refusing to mark validation passed.')
    archive = BASE / f'fire-claws-test-results-{DATE}'
    final = archive / 'final'
    final.mkdir(parents=True, exist_ok=True)
    for module in ('core', 'server/d2gs'):
        for xml in (ROOT / module / 'build/test-results/test').glob('TEST-*.xml'):
            shutil.copy2(xml, final / xml.name)
    stages = {name: [suite(xml) for xml in sorted((archive / name).glob('TEST-*.xml'))]
              for name in ('initial', 'final')}
    totals = {key: sum(s[key] for s in stages['final'])
              for key in ('tests', 'failures', 'errors', 'skipped')}
    expected = {'com.riiablo.presentation.FireClawsPresentationTest',
                'com.riiablo.engine.server.FireClawsAnimationDataTest',
                'com.riiablo.engine.server.DruidFeralMaulTest',
                'com.riiablo.engine.client.UnitOverlayPacketTest',
                'com.riiablo.server.d2gs.UnitOverlayTransportTest'}
    assert expected.issubset({s['suite'] for s in stages['final']})
    assert totals['tests'] > 0 and totals['failures'] == totals['errors'] == totals['skipped'] == 0
    inventory = BASE / 'class-skill-batch-2026-11-21/class-skill-inventory.json'
    data = json.loads(inventory.read_text(encoding='utf-8-sig'))
    skill = next(skill for group in data['classes'] for skill in group['skills'] if skill['skill_id'] == 239)
    source_paths = [
        ROOT / 'core/src/main/java/com/riiablo/engine/server/Actioneer.java',
        ROOT / 'core/src/main/java/com/riiablo/presentation/OverlayManager.java',
        ROOT / 'core/src/main/java/com/riiablo/presentation/component/Overlay.java',
        ROOT / 'core/src/main/java/com/riiablo/engine/client/ClientNetworkReceiver.java',
        ROOT / 'server/d2gs/src/main/java/com/riiablo/server/d2gs/NetworkSynchronizer.java',
        ROOT / 'server-domain/src/main/java/com/riiablo/engine/server/event/UnitOverlayEvent.java',
        ROOT / 'core/src/main/flatbuffers/com/riiablo/net/d2gs/Sync.fbs',
        ROOT / 'core/src/main/flatbuffers/com/riiablo/net/d2gs/D2GS.fbs',
        Path('F:/3rd_src/D2MOO/source/D2Game/src/SKILLS/Skills.cpp'),
        Path('F:/3rd_src/D2MOO/source/D2Game/src/SKILLS/SkillDruid.cpp'),
        Path('F:/3rd_src/D2MOO/source/D2Game/src/UNIT/SUnitDmg.cpp'),
        inventory,
    ]
    report = {
        'schema': 1, 'date': DATE, 'skill': 239,
        'evidence_scope': 'Effective local MPQ tables and real COF/DCC/AnimData reads; public reconstructed D2MOO source; headless production ECS and packet integration. No original runtime, socket session or GPU validation.',
        'original_executed': False, 'foreground_used': False, 'global_input_used': False,
        'java_home': 'C:/Program Files/Java/jdk-17', 'final_validation': totals,
        'interrupted_unfiltered_core_run': 'fire-claws-final-validation-2026-11-24.log: --tests applied only to the last requested task, causing an unintended broad core run. Interrupted after unrelated failures including missing .d2i test assets. No finalized XML available; old XML was not treated as its results.',
        'stages': stages, 'skill_row': skill['row'], 'overlay_row': data['overlay_rows']['fire_hit'],
        'resource_discrepancy': {'table_frames': 8, 'actual_dcc_frames': 12, 'directions': 1,
                                 'native_runtime_lifetime_verified': False},
        'source_sha256': {str(path): digest(path) for path in source_paths},
        'logs': {path.name: digest(path) for path in BASE.glob(f'fire-claws-*-{DATE}.log')},
        'remaining': ['Original live target overlay birth/expiry and table Frames=8 vs DCC frames=12 interpretation',
                      'GPU palette/occlusion/dynamic lighting and full equipment/speed/direction matrix',
                      'Socket end-to-end ordering across target death/deletion/visibility boundaries'],
    }
    output = BASE / f'fire-claws-presentation-validation-{DATE}.json'
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    manifest = BASE / 'class-skill-batch-2026-11-21/dru-manifest.json'
    document = json.loads(manifest.read_text(encoding='utf-8-sig'))
    entry = next(item for item in document['skills'] if item['skill_id'] == 239)
    assert entry['runtime_status'] == 'not_run_in_class_batch'
    entry['comparison_status'] = 'successful_hit_overlay_transport_and_shape_a1_headless_validated'
    entry['execution_readiness'] = 'needs_private_desktop_fire_claws_target_overlay_runtime_capture'
    entry['individual_progress'] = {
        'status': 'missing_skill_target_flash_repaired_with_authoritative_local_and_d2gs_event',
        'analysis': f'docs/druid-fire-claws-presentation-analysis-{DATE}.md',
        'validation': output.relative_to(ROOT).as_posix(),
        'validated': f"{totals['tests']} JDK 17 headless tests passed. SrvSt58 hit/miss/block/zero/lethal/dead target and one-shot consumption; native fire_hit drawing policy, remote ID mapping and level scoping; both shape A1 COF/DCC/AnimData resources.",
        'remaining': '; '.join(report['remaining']), 'evidence_scope': report['evidence_scope'],
    }
    manifest.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'report': str(output), 'totals': totals}, ensure_ascii=False))


if __name__ == '__main__':
    main()
