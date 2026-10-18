"""Summarize MonsterAiArena action timing without equating damage with attacks."""
import argparse
import collections
import csv
import html
import json
import math
import os
import re
from pathlib import Path


def percentile(values, fraction):
    return sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)] if values else None


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('inputs', nargs='+', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    cases, raw = [], []
    for directory in args.inputs:
        if not (directory / 'COMPLETED').exists():
            raise SystemExit('Incomplete arena: ' + str(directory))
        rows = [json.loads(line) for line in (directory / 'results.jsonl').read_text('utf-8').splitlines()]
        for row in rows:
            if row['status'] == 'NOT_RUN':
                continue
            raw.append(row)
            actions = row['actionTimings']
            # Replay immutable projectile logs with one consistent attribution rule,
            # including runs collected while the observer was being refined.
            for action in actions:
                action['projectileTick'] = -1
                action['projectiles'] = 0
            log_path = directory / row['log']
            for tick_text, skill_text in re.findall(
                    r'tick=(\d+) ProjectileCreated subject=\d+ entity=\d+ skill=(-?\d+)',
                    log_path.read_text('utf-8', errors='replace')):
                tick, skill = int(tick_text), int(skill_text)
                for action in reversed(actions):
                    if action['decisionTick'] > tick or action['executionTick'] < 0 or action['executionTick'] > tick:
                        continue
                    if tick - action['executionTick'] > 1:
                        break
                    if action['skill'] != skill and not (skill < 0 and action['skill'] == 0):
                        continue
                    if action['projectileTick'] < 0:
                        action['projectileTick'] = tick
                    action['projectiles'] += 1
                    break
            # Targeted skills remain explicitly separate: curses/teleports are not necessarily attacks.
            basic = [a for a in actions if a['kind'] == 'BASIC_ATTACK']
            aimed = [a for a in actions if a['kind'] == 'PLAYER_TARGET_SKILL']
            done = [a for a in basic if a['executionTick'] >= 0]
            failed = [a for a in basic if a['executionTick'] < 0 and a['outcome'] != 'CENSORED_AT_TEST_END']
            def physical_tick(action):
                return min((action.get(k, -1) for k in ('meleeAttemptTick', 'projectileTick')
                    if action.get(k, -1) >= 0), default=-1)
            actual = [a for a in basic if physical_tick(a) >= 0]
            physical_actions = [a for a in actions if physical_tick(a) >= 0]
            latency = [physical_tick(a) - a['decisionTick'] for a in actual]
            aimed_latency = [a['executionTick'] - a['decisionTick'] for a in aimed if a['executionTick'] >= 0]
            aimed_failed = [a for a in aimed if a['executionTick'] < 0 and a['outcome'] != 'CENSORED_AT_TEST_END']
            first_decision = min((a['decisionTick'] for a in basic), default=None)
            first_execution = min((physical_tick(a) for a in actual), default=None)
            flags = []
            if failed:
                flags.append('BASIC_NOT_EXECUTED')
            if aimed_failed:
                flags.append('TARGETED_SKILL_NOT_EXECUTED')
            if latency and max(latency) > 50:
                flags.append('BASIC_LATENCY_GT_2S')
            if first_execution is not None and first_execution > 125:
                flags.append('FIRST_BASIC_AFTER_5S')
            if not actual:
                flags.append('NO_BASIC_OBSERVED')
            if any(physical_tick(a) < 0 for a in done):
                flags.append('BASIC_CALLBACK_WITHOUT_PHYSICAL_EVIDENCE')
            if row['status'] in ('RUNTIME_ERROR', 'ERROR_LOG', 'AI_FALLBACK'):
                flags.append(row['status'])
            if row['unmatchedExecutions']:
                flags.append('UNMATCHED_CALLBACK')
            cases.append(dict(monster=row['monster'], ai=row['ai'], scenario=row['scenario'],
                seed=row['seed'], difficulty=row['difficulty'], ticks=row['ticks'], status=row['status'],
                decisions=len(basic), executed=len(actual), callbacks=len(done), failed=len(failed),
                callbacks_without_physical=sum(physical_tick(a) < 0 for a in done),
                censored=sum(a['outcome'] == 'CENSORED_AT_TEST_END' for a in basic),
                first_decision=first_decision, first_execution=first_execution,
                first_targeted_decision=min((a['decisionTick'] for a in aimed), default=None),
                first_targeted_execution=min((a['executionTick'] for a in aimed if a['executionTick'] >= 0), default=None),
                first_action_decision=min((a['decisionTick'] for a in actions), default=None),
                first_action_execution=min((a['executionTick'] for a in actions if a['executionTick'] >= 0), default=None),
                physical_actions=len(physical_actions),
                first_physical_action=min((physical_tick(a) for a in physical_actions), default=None),
                maximum_physical_action_latency=max((physical_tick(a) - a['decisionTick'] for a in physical_actions), default=None),
                median=percentile(latency, .5), p95=percentile(latency, .95), maximum=max(latency, default=None),
                melee_attempts=sum(a.get('meleeAttempts', 0) for a in actions),
                melee_misses=sum(a.get('meleeMisses', 0) for a in actions),
                targeted_skill_decisions=len(aimed), targeted_skill_executed=len(aimed_latency),
                targeted_skill_failed=len(aimed_failed),
                targeted_skill_max=max(aimed_latency, default=None),
                other_actions=sum(a['kind'] not in ('BASIC_ATTACK', 'PLAYER_TARGET_SKILL') for a in actions),
                unmatched=row['unmatchedExecutions'], flags=';'.join(flags),
                log=os.path.relpath(directory / row['log'], args.output).replace(os.sep, '/')))
    summary = dict(variants=len({r['monster'] for r in cases}), ais=len({r['ai'] for r in cases}),
        scenes=len(cases), seeds=sorted({r['seed'] for r in cases}),
        statuses=dict(collections.Counter(r['status'] for r in cases)),
        basic_decisions=sum(r['decisions'] for r in cases), basic_executed=sum(r['executed'] for r in cases),
        basic_callbacks=sum(r['callbacks'] for r in cases),
        physical_actions=sum(r['physical_actions'] for r in cases),
        callbacks_without_physical=sum(r['callbacks_without_physical'] for r in cases),
        basic_failed=sum(r['failed'] for r in cases), basic_censored=sum(r['censored'] for r in cases),
        melee_attempts=sum(r['melee_attempts'] for r in cases), melee_misses=sum(r['melee_misses'] for r in cases),
        failed_ais=sorted({r['ai'] for r in cases if r['failed']}),
        no_basic_ais=sorted({r['ai'] for r in cases if not r['executed']}),
        unmatched=sum(r['unmatched'] for r in cases))
    all_latencies = [physical_tick(a) - a['decisionTick'] for row in raw
        for a in row['actionTimings'] if a['kind'] == 'BASIC_ATTACK' and physical_tick(a) >= 0]
    summary.update(basic_latency_median=percentile(all_latencies, .5),
        basic_latency_p95=percentile(all_latencies, .95), basic_latency_max=max(all_latencies, default=None),
        targeted_skill_failed=sum(r['targeted_skill_failed'] for r in cases))
    (args.output / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), 'utf-8')
    with (args.output / 'cases.csv').open('w', encoding='utf-8-sig', newline='') as file:
        writer = csv.DictWriter(file, fieldnames=list(cases[0]))
        writer.writeheader(); writer.writerows(cases)
    # This extends the existing arena HTML diagnostic, with locally linked raw event logs.
    columns = list(cases[0])
    output = ['<!doctype html><meta charset="utf-8"><title>怪物攻击时间测试</title>',
        '<style>body{font:14px system-ui;margin:24px;color:#222}table{border-collapse:collapse}',
        'td,th{padding:6px;border-bottom:1px solid #ddd;white-space:nowrap}th{position:sticky;top:0;background:#eee;cursor:pointer}',
        'input{padding:8px;width:360px}pre{white-space:pre-wrap}</style>',
        '<h1>怪物攻击决定 → 执行时间</h1>',
        '<p>25 帧/秒；每场 1500 帧（60 秒）。每场独立角色和怪物，使用正式 AI、动画与技能系统。',
        'decision 是已下达的 Casting/Sequence 动作，未覆盖进入动作创建前就失败的意图。executed / first_execution / median / p95 / maximum 采用',
        '真实近战尝试或实体弹道创建时刻；callbacks 单列 SkillDo 关键帧。不是命中或扣血。弹道观察可能因 ECS flush 延迟 1 帧，飞行到命中时间不计入起手延迟。</p>',
        '<p>BASIC 仅 A1/A2 普攻；S1/S2 示威、变身等单列 other_actions。targeted_skill 可能含诅咒和移动技能，',
        '不能全部视作伤害攻击。NO_BASIC_OBSERVED 是待检查线索，施法型、机关、出生点及主人依赖怪物可能正常。',
        'CENSORED 是测试结束时仍在动作中，不能判卡死。无网络、复杂地形或完整任务条件。</p>',
        '<p><b>' + str(summary['variants']) + ' 个变体 · ' + str(summary['ais']) + ' 种 AI · '
        + str(summary['scenes']) + ' 个场景</b>。已确认真实普攻 ' + str(summary['basic_executed'])
        + ' 次；普攻起手中位数 / P95 / 最大值（帧）：' + str(summary['basic_latency_median'])
        + ' / ' + str(summary['basic_latency_p95']) + ' / ' + str(summary['basic_latency_max'])
        + '。<a href="cases.csv">下载 CSV</a> · <a href="findings.md">问题结论</a></p>'
        + '<details><summary>完整统计</summary><pre>'
        + html.escape(json.dumps(summary, ensure_ascii=False, indent=2)) + '</pre></details>',
        '<label>搜索怪物 / AI / 标记 <input id="filter"></label>',
        '<table id="results"><thead><tr>' + ''.join('<th>' + c + '</th>' for c in columns) + '</tr></thead><tbody>']
    for case in cases:
        output.append('<tr>' + ''.join('<td>' + ('<a href="' + html.escape(str(value), quote=True) + '">日志</a>' if key == 'log'
            else html.escape(str(value) if value is not None else '—')) + '</td>' for key, value in case.items()) + '</tr>')
    output.append('''</tbody></table><script>
const table=document.querySelector('#results'),body=table.tBodies[0];
document.querySelector('#filter').oninput=e=>{const q=e.target.value.toLowerCase();
Array.from(body.rows).forEach(r=>r.hidden=!r.textContent.toLowerCase().includes(q));};
Array.from(table.tHead.rows[0].cells).forEach((h,i)=>h.onclick=()=>{h.asc=!h.asc;
const rows=Array.from(body.rows);rows.sort((a,b)=>{const x=a.cells[i].textContent,y=b.cells[i].textContent;
return (h.asc?1:-1)*(x!==''&&y!==''&&Number.isFinite(+x)&&Number.isFinite(+y)?+x-+y:x.localeCompare(y));});
rows.forEach(r=>body.appendChild(r));});</script>''')
    (args.output / 'report.html').write_text(''.join(output), 'utf-8')
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
