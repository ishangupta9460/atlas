import { fireEvent, render, screen, within } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import ScheduleTimeline from "./ScheduleTimeline";
import { Workspace } from "./execution";

function fixture(): Workspace {
  return { serverTime: "2026-09-26T04:00:00Z", timezone: "Asia/Kolkata", history: [], fixed: [],
    tasks: [{ id: 1, title: "Scheduled work", completionCriterion: "Outcome", description: null, workState: "ready", completionPct: 0, importance: "medium", flexibilityTier: "flexible", deadline: null, goalId: null, goalTitle: null, goalState: null, milestoneTitle: null, categoryName: null, blockers: 0 }],
    blocks: [{ id: 1, commitmentId: 1, startTime: "2026-09-26T04:00:00Z", endTime: "2026-09-26T04:30:00Z", state: "scheduled", placementReason: "Generated", sessionState: null, actualStart: null, runningSince: null, activeMillis: 0, userMovedFlag: false }],
  };
}
function show(data = fixture()) {
  const move = vi.fn(async () => {}), changeDay = vi.fn(), select = vi.fn();
  render(<ScheduleTimeline data={data} now={Date.parse(data.serverTime)} select={select} capture={() => {}} selectedDay={null} changeDay={changeDay} busy={false} move={move} />);
  return { data, move, changeDay, select };
}

it("renders the saved timezone, real placements, empty days and week navigation", () => {
  const { changeDay, select } = show();
  const calendar = screen.getByRole("region", { name: "Week calendar" });
  expect(within(calendar).getAllByText("No recorded windows")).toHaveLength(6);
  expect(within(calendar).getByText("Scheduled by Atlas")).toBeInTheDocument();
  expect(within(calendar).getByText(/9:30.*10:00/)).toBeInTheDocument();
  fireEvent.click(within(calendar).getByRole("button", { name: "Scheduled work" })); expect(select).toHaveBeenCalledWith(1);
  fireEvent.click(screen.getByRole("button", { name: "Next week" }));
  expect(changeDay).toHaveBeenLastCalledWith("2026-10-02T18:30:00.000Z");
  fireEvent.click(screen.getByRole("button", { name: "Previous week" }));
  expect(changeDay).toHaveBeenLastCalledWith("2026-09-18T18:30:00.000Z");
});

it("maps a drag to an absolute UTC destination without mutating the visible block", () => {
  const { data, move } = show();
  const source = within(screen.getByRole("region", { name: "Week calendar" })).getByRole("button", { name: "Scheduled work" }).closest("article")!;
  const transfer = { setData: vi.fn(), getData: () => "1" };
  fireEvent.dragStart(source, { dataTransfer: transfer }); expect(transfer.setData).toHaveBeenCalledWith("text/plain", "1");
  expect(source.style.pointerEvents).toBe("none");
  const target = screen.getByRole("button", { name: /Move here 2026-09-27 10:00 AM/i });
  fireEvent.dragOver(target, { dataTransfer: transfer }); fireEvent.drop(target, { dataTransfer: transfer });
  fireEvent.dragEnd(source); expect(source.style.pointerEvents).not.toBe("none");
  expect(move).toHaveBeenCalledWith(data.blocks[0], "2026-09-27T04:30:00.000Z");
  expect(data.blocks[0].startTime).toBe("2026-09-26T04:00:00Z");
});

it("supports keyboard move selection and renders sticky state from the server", () => {
  const data=fixture(); data.blocks[0].userMovedFlag=true; const { move }=show(data);
  expect(screen.getByText("You chose this time · sticky")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Move Scheduled work" }));
  fireEvent.click(screen.getByRole("button", { name: /Move here 2026-09-26 11:00 AM/i }));
  expect(move).toHaveBeenCalledWith(data.blocks[0], "2026-09-26T05:30:00.000Z");
});

it("keeps fixed and active windows immovable and renders overlapping lanes", () => {
  const data=fixture(); data.blocks[0].state="active"; data.blocks[0].sessionState="running";
  data.fixed=[{id:2,title:"Fixed meeting",startTime:data.blocks[0].startTime,endTime:data.blocks[0].endTime}]; show(data);
  const calendar=screen.getByRole("region", {name:"Week calendar"});
  expect(within(calendar).queryByRole("button",{name:"Move Scheduled work"})).not.toBeInTheDocument();
  const articles=calendar.querySelectorAll("article");
  expect(articles).toHaveLength(2); expect(articles[0].style.left).not.toBe(articles[1].style.left);
  expect([...articles].every(a=>a.draggable===false)).toBe(true);
});

it("offers both repeated DST clock times as distinct absolute slots", () => {
  const data=fixture(); data.timezone="America/New_York"; data.serverTime="2026-11-01T12:00:00Z"; data.blocks=[]; show(data);
  const slots=screen.getAllByRole("button", {name:/Move here 2026-11-01 1:30 AM/i});
  expect(slots).toHaveLength(2);
  expect(slots[0].getAttribute("aria-label")).not.toBe(slots[1].getAttribute("aria-label"));
});
