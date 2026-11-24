"""Archive the bounded, headless Shock Wave audit; never launch the original game."""
import hashlib
import json
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'tools/original-observer'
DATE = '2026-11-24'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def suites(folder):
    result = []
    for path in sorted(folder.glob('TEST-*.xml')):
        tree = ET.parse(path).getroot()
        result.append({
            'suite': tree.get('name'),
            **{k: int(tree.get(k, 0)) for k in ('tests', 'failures', 'errors', 'skipped')},
            'cases': [case.get('name') for case in tree.findall('testcase')],
            'stdout': '\n'.join(item.text or '' for item in tree.findall('system-out')),
            'failure_details': [item.text for item in tree.findall('.//failure')],
            'xml': path.relative_to(ROOT).as_posix(), 'sha256': sha(path),
        })
    return result


def main():
    log = BASE / f'shock-wave-final3-validation-{DATE}.log'
    assert 'BUILD SUCCESSFUL' in log.read_text(encoding='utf-8', errors='replace'), 'Final test run has not succeeded'
    archive = BASE / f'shock-wave-test-results-{DATE}'
    final = archive / 'final'
    final.mkdir(parents=True, exist_ok=True)
    for path in (ROOT / 'core/build/test-results/test').glob('TEST-*.xml'):
        shutil.copy2(path, final / path.name)
    stages = {name: suites(archive / name) for name in ('initial', 'audio-fixture', 'final')}
    required = {'com.riiablo.engine.server.DruidShockWaveTest',
                'com.riiablo.engine.server.ShockWaveAnimationDataTest',
                'com.riiablo.presentation.ShockWavePresentationTest',
                'com.riiablo.presentation.ShockWaveImpactAudioTest'}
    assert required.issubset({item['suite'] for item in stages['final']})
    totals = {k: sum(item[k] for item in stages['final']) for k in ('tests', 'failures', 'errors', 'skipped')}
    assert totals['tests'] > 0 and totals['failures'] == totals['errors'] == totals['skipped'] == 0
    inventory = BASE / 'class-skill-batch-2026-11-21/class-skill-inventory.json'
    data = json.loads(inventory.read_text(encoding='utf-8-sig'))
    skill = next(s for c in data['classes'] for s in c['skills'] if s['skill_id'] == 243)
    paths = [
        ROOT / 'core/src/main/java/com/riiablo/presentation/MissileImpactPresentationSystem.java',
        ROOT / 'core/src/main/java/com/riiablo/presentation/MissileLoader.java',
        ROOT / 'core/src/main/java/com/riiablo/presentation/SkillCastHandler.java',
        ROOT / 'core/src/main/java/com/riiablo/presentation/StateOverlaySystem.java',
        ROOT / 'core/src/main/java/com/riiablo/engine/server/ServerSkillSystem.java',
        ROOT / 'core/src/main/java/com/riiablo/engine/server/MissileCollisionSystem.java',
        ROOT / 'core/src/main/java/com/riiablo/engine/server/component/CofReference.java',
        Path('F:/3rd_src/D2MOO/source/D2Game/src/SKILLS/SkillAma.cpp'),
        Path('F:/3rd_src/D2MOO/source/D2Game/src/SKILLS/Skills.cpp'),
        Path('F:/3rd_src/D2MOO/source/D2Game/src/MISSILES/MissMode.cpp'),
        Path('F:/3rd_src/D2MOO/source/D2Common/src/D2Skills.cpp'), inventory,
    ]
    for folder in ('engine/server', 'presentation'):
        paths += list((ROOT / 'core/src/test/java/com/riiablo' / folder).glob('*ShockWave*.java'))
    report = {
        'schema': 1, 'date': DATE, 'skill': 243,
        'evidence_scope': 'Effective local MPQ tables, real AnimData/COF/DCC header reads, public reconstructed D2MOO source, headless production ECS presentation and combat/state snapshot tests. No original runtime or GPU/socket session.',
        'original_executed': False, 'foreground_used': False, 'global_input_used': False,
        'java_home': 'C:/Program Files/Java/jdk-17', 'final_validation': totals, 'stages': stages,
        'intermediate_compile_failure': 'shock-wave-final-validation-2026-11-24.log: test used lowercase Files.sounds instead of Files.Sounds; corrected before final successful run.',
        'intermediate_fixture_failure': 'shock-wave-final2-validation-2026-11-24.log: 36 tests, 1 failure because the new audio fixture omitted OverlayManager; registered the dependency. XML retained under audio-fixture.',
        'skill_row': skill['row'], 'missile_row': data['missile_rows']['shockwave'],
        'state_row': data['state_rows']['stunned'], 'overlay_row': data['overlay_rows']['stun'],
        'prior_inventory_resource_records': {k: v for k, v in data['resources'].items() if k.lower().endswith('expansion\\shockwave.dcc')},
        'resource_observations': {
            'bear_cast': {'key': 'TGSCHTH', 'frames': 10, 'directions': 8, 'base_speed': 152, 'action_markers': 1},
            'wave': {'frames': 7, 'directions': 8, 'frame_delta': 256},
            'stun': {'frames': 12, 'directions': 1, 'fps': 25},
        },
        'repair': 'SrvDmg07 unit impacts use hit class 96 (impact_stun_1), overriding shockwave raw HitClass=64 (impact_lightning_1); no extra visual or damage generated.',
        'remaining': ['Original live animation birth/expiry phase and exact path stepping',
                      'All-direction sprite pixel orientation, GPU palette/occlusion and full equipment/speed matrix',
                      'Original audio variants and live socket/collision timing'],
        'source_sha256': {str(path): sha(path) for path in paths},
        'logs': {path.name: sha(path) for path in BASE.glob(f'shock-wave-*-{DATE}.log')},
    }
    output = BASE / f'shock-wave-presentation-validation-{DATE}.json'
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    manifest = BASE / 'class-skill-batch-2026-11-21/dru-manifest.json'
    document = json.loads(manifest.read_text(encoding='utf-8-sig'))
    entry = next(item for item in document['skills'] if item['skill_id'] == 243)
    assert entry['runtime_status'] == 'not_run_in_class_batch'
    entry['comparison_status'] = 'bear_cast_five_wave_and_stun_presentation_headless_validated'
    entry['execution_readiness'] = 'needs_private_desktop_shock_wave_presentation_runtime_capture'
    entry['individual_progress'] = {
        'status': 'core_presentation_chain_verified_and_srv_dmg07_hit_audio_repaired',
        'analysis': f'docs/druid-shock-wave-presentation-analysis-{DATE}.md',
        'validation': output.relative_to(ROOT).as_posix(),
        'validated': f"{totals['tests']} JDK 17 headless tests passed. Actual bear SC/wave/stun resources; five owned local-server sprites without duplicate client fan; stun snapshot lifetime and shared combat regressions; corrected lightning-to-stun impact audio.",
        'remaining': '; '.join(report['remaining']), 'evidence_scope': report['evidence_scope'],
    }
    manifest.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'report': str(output), 'totals': totals}, ensure_ascii=False))


if __name__ == '__main__':
    main()
