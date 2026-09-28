import { useEffect, useRef, useState } from "react";
import { Block, Workspace, date, time } from "./execution";
import { addDays, dayStart, formatInZone } from "./executionTime";
import { dayItems, timelineItems } from "./dayTimeline";

export default function WeekCalendar({ data, days, busy, move, select }: {
  data: Workspace; days: string[]; busy: boolean;
  move: (block: Block, start: string) => Promise<void>; select: (id: number) => void;
}) {
  const [moving, setMoving] = useState<number | null>(null);
  const [dragging, setDragging] = useState(false);
  const calendar = useRef<HTMLDivElement>(null);
  useEffect(() => { if (calendar.current) calendar.current.scrollTop = 420; }, [days[0]]);
  const items = timelineItems(data);
  async function drop(id: number, at: number) {
    const block = data.blocks.find(b => b.id === id && b.state === "scheduled");
    if (!block || !block.commitmentId || busy) return;
    await move(block, new Date(at).toISOString()); setMoving(null);
  }
  return <>
    <p className="hint">Drag an unstarted block to a time slot, or select Move and then a slot. Open a task brief to enter an exact time.</p>
    {moving !== null && <p role="status">Choose a destination for this block. <button onClick={() => setMoving(null)}>Cancel move</button></p>}
    <div ref={calendar} className="week-calendar" role="region" aria-label="Week calendar">
      {days.map(day => {
        const start = dayStart(day, data.timezone), end = dayStart(addDays(day, 1), data.timezone);
        const entries = dayItems(items, start, data.timezone);
        const slots: number[] = [];
        for (let at = start; at < end; at += 30 * 60000) slots.push(at);
        const lanes: number[] = [];
        const placed = entries.map(item => {
          const top = (Math.max(item.start, start) - start) / 60000;
          const height = Math.max(96, (Math.min(item.end, end) - Math.max(item.start, start)) / 60000);
          let lane = lanes.findIndex(bottom => bottom <= top);
          if (lane < 0) lane = lanes.length;
          lanes[lane] = top + height;
          return { item, top, height, lane };
        });
        return <section key={day} className="week-day" aria-label={day}>
          <h3>{date(new Date(start).toISOString(), data.timezone)}</h3>
          <span className="hint">{entries.length ? `${entries.length} recorded windows` : "No recorded windows"}</span>
          <div className="week-day-grid" style={{ height: (end - start) / 60000 }}>
            {slots.map(at => <button key={at} className="week-slot" disabled={busy} style={{ top: (at - start) / 60000 }}
              aria-label={`Move here ${day} ${formatInZone(new Date(at).toISOString(), data.timezone, "slot")}`}
              onClick={() => { if (moving !== null) void drop(moving, at); }}
              onDragOver={e => { if (!busy) e.preventDefault(); }}
              onDrop={e => { e.preventDefault(); void drop(Number(e.dataTransfer.getData("text/plain")), at); }}>
              {time(new Date(at).toISOString(), data.timezone)}
            </button>)}
            {placed.map(({ item, top, height, lane }) => {
              const block = item.kind === "work" ? data.blocks.find(b => `work-${b.id}` === item.key) : undefined;
              return <article key={item.key} className={`week-block ${item.kind}`} style={{ top, height, overflow: "auto", pointerEvents: dragging || moving !== null ? "none" : undefined, left: `${lane / lanes.length * 100}%`, width: `${100 / lanes.length}%` }}
                draggable={!busy && block?.state === "scheduled" && !!block.commitmentId}
                onDragStart={e => { if (block) { e.dataTransfer.setData("text/plain", String(block.id)); setDragging(true); } }}
                onDragEnd={() => setDragging(false)}>
                {item.taskId !== null ? <button className="text-button" onClick={() => select(item.taskId!)}>{item.title}</button> : <strong>{item.title}</strong>}
                <span>{time(new Date(item.start).toISOString(), data.timezone)}–{time(new Date(item.end).toISOString(), data.timezone)}</span>
                <span>{item.kind === "fixed" ? "Fixed" : block?.userMovedFlag ? "You chose this time · sticky" : "Scheduled by Atlas"}</span>
                {block?.state === "scheduled" && !!block.commitmentId && <button disabled={busy} onClick={() => setMoving(block.id)}>Move {item.title}</button>}
              </article>;
            })}
          </div>
        </section>;
      })}
    </div>
  </>;
}
