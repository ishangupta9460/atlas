import { Block, Workspace, date as formatDate, duration, label, time as formatTime } from "./execution";
import { dayItems, gapsBetween, itemStatus, timelineItems } from "./dayTimeline";

import { addDays, dateKey, dayStart, displayZone, localInput } from "./executionTime";
import WeekCalendar from "./WeekCalendar";

const stamp = (n: number) => new Date(n).toISOString();
export default function ScheduleTimeline({ data, now, select, capture, selectedDay, changeDay, busy, move }: { busy: boolean; move: (block: Block, start: string) => Promise<void>; data: Workspace; now: number; select: (id: number) => void; capture: () => void; selectedDay: string | null; changeDay: (day: string | null) => void }) {
  const time = (value: string) => formatTime(value, data.timezone);
  const date = (value: string) => formatDate(value, data.timezone);
  const key = dateKey(Date.parse(selectedDay ?? stamp(now)), data.timezone);
  const start = dayStart(key, data.timezone), end = dayStart(addDays(key, 1), data.timezone);
  const isToday = key === dateKey(now, data.timezone);
  const moveWeek = (amount: number) => changeDay(stamp(dayStart(addDays(key, amount), data.timezone)));
  const all = timelineItems(data);
  const items = dayItems(all, start, data.timezone);
  const weekday = new Date(`${key}T12:00:00Z`).getUTCDay();
  const weekStart = addDays(key, -((weekday + 6) % 7));
  const days = Array.from({ length: 7 }, (_, i) => addDays(weekStart, i));
  const atHour = (h: number) => localInput(`${key}T${String(h).padStart(2, "0")}:00`, data.timezone);
  const visibleStart = Math.max(start, Math.min(atHour(8), ...items.map(i => i.start), ...(isToday ? [now - 1800000] : [])));
  const visibleEnd = Math.min(end, Math.max(atHour(18), ...items.map(i => i.end), ...(isToday ? [now + 1800000] : [])));
  const axisStart = Math.max(start, Math.floor(visibleStart / 3600000) * 3600000);
  const axisEnd = Math.min(end, Math.ceil(visibleEnd / 3600000) * 3600000);
  const height = (axisEnd - axisStart) / 60000 * 2;
  const y = (n: number) => (n - axisStart) / 60000 * 2;
  // Short entries have a readable minimum height. Lane assignment includes that height.
  const lanes: number[] = [];
  const placed = items.map(item => {
    const top = y(Math.max(item.start, axisStart));
    const size = Math.max(112, y(Math.min(item.end, axisEnd)) - top);
    let lane = lanes.findIndex(bottom => bottom <= top);
    if (lane === -1) lane = lanes.length;
    lanes[lane] = top + size;
    return { item, top, size, lane };
  });
  const ticks: number[] = [];
  for (let n = start; n <= axisEnd; n += 3600000) if (n >= axisStart) ticks.push(n);
  const gaps = gapsBetween(items, axisStart, axisEnd);
  return <section aria-label="Schedule timeline">
    <div className="section-heading"><h2>{date(stamp(start))}</h2><div className="actions"><button aria-label="Previous week" onClick={() => moveWeek(-7)}>←</button><button onClick={() => changeDay(null)}>Jump to today</button><button aria-label="Next week" onClick={() => moveWeek(7)}>→</button></div></div>
    <div className="week-strip" aria-label="Choose a day">{days.map(d => <button key={d} aria-pressed={d === key} aria-label={date(stamp(dayStart(d, data.timezone)))} onClick={() => changeDay(stamp(dayStart(d, data.timezone)))}><span>{d}</span><span className="hint">{dayItems(all, dayStart(d, data.timezone), data.timezone).length || "—"}</span></button>)}</div>
    <p className="hint timeline-caption">{displayZone(data.timezone)} · Scheduled work and fixed commitments. Labels show exact times.</p>
    <WeekCalendar data={data} days={days} busy={busy} move={move} select={select} />
    <h3>Selected day · {date(stamp(start))}</h3>
    {!items.length && <div className="timeline-empty"><p>Room to make a plan. Capture a task or choose one below.</p><button onClick={capture}>Add a task</button></div>}
    <div className="timeline-scroll" tabIndex={0} role="region" aria-label="Day timeline, scroll to explore"><div className="day-timeline" style={{ height: Math.max(height + 40, ...placed.map(p => p.top + p.size + 20)), minWidth: lanes.length > 1 ? lanes.length * 210 + 80 : undefined }}>
      {ticks.map(n => <div className="timeline-tick" key={n} style={{ top: y(n) }}><time dateTime={stamp(n)}>{time(stamp(n))}</time></div>)}
      {gaps.filter(g => g.end - g.start >= 30 * 60000).map(g => {
        const top = Math.max(y(g.start) + 18, ...placed.filter(p => p.item.end <= g.start).map(p => p.top + p.size + 8));
        return top + 20 < y(g.end) ? <div className="timeline-gap" key={g.start} style={{ top }}>{duration(g.end - g.start)} unplanned</div> : null;
      })}
      <ol className="timeline-entries" aria-label="Recorded windows">{placed.map(({ item, top, size, lane }) => <li className={`timeline-entry ${item.kind} ${item.state}`} key={item.key} style={{ top, height: size, left: `${lane / Math.max(1, lanes.length) * 100}%`, width: `${100 / Math.max(1, lanes.length)}%` }}>
        <span className="hint">{time(stamp(item.start))}–{time(stamp(item.end))}{item.start < start ? " · from previous day" : ""}{item.end > end ? " · into next day" : ""}</span>
        {item.taskId !== null ? <button className="text-button timeline-title" title={item.title} onClick={() => select(item.taskId!)}>{item.title}</button> : <strong title={item.title}>{item.title}</strong>}
        <span className="hint">{itemStatus(item, now)}{item.kind === "work" ? ` · ${label(item.flexibility)} · ${data.blocks.find(b => `work-${b.id}` === item.key)?.userMovedFlag ? "You chose this time · sticky" : "Scheduled by Atlas"}` : ""}</span>
      </li>)}</ol>
      {now >= axisStart && now < axisEnd && <div className="timeline-now" style={{ top: y(now) }}><span>Now · {time(stamp(now))}</span></div>}
    </div></div>
    <details className="disclosure"><summary>Plan another task</summary><ul className="row-list">{data.tasks.filter(t => t.workState === "draft" || t.workState === "ready").map(t => <li className="row-item" key={t.id}><div className="row-item-main"><h3>{t.title ?? "Untitled draft"}</h3></div><button onClick={() => select(t.id)}>{t.workState === "draft" ? "Make ready" : "Schedule this"}</button></li>)}</ul><button onClick={capture}>Add a task</button></details>
  </section>;
}
