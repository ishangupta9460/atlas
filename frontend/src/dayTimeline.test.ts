import { expect, it } from "vitest";
import { DayItem, dayBounds, dayItems, gapsBetween, itemStatus } from "./dayTimeline";

const item = (start: number, end: number): DayItem => ({ key: String(start), title: "Work", start, end, kind: "work", state: "scheduled", taskId: 1, flexibility: "flexible" });
it("merges overlapping and nested windows when showing empty time", () => {
  expect(gapsBetween([item(20, 50), item(10, 30), item(25, 35), item(60, 90)], 0, 100)).toEqual([{ start: 0, end: 10 }, { start: 50, end: 60 }, { start: 90, end: 100 }]);
});
it("clips cross-midnight windows to the selected local day and excludes boundary-only contact", () => {
  const { start, end } = dayBounds(new Date(2026, 8, 24, 12).getTime());
  const overnight = item(start - 3600000, start + 3600000);
  expect(dayItems([overnight, item(start - 7200000, start), item(end, end + 3600000)], start)).toEqual([overnight]);
  expect(gapsBetween([overnight], start, end)).toEqual([{ start: start + 3600000, end }]);
});
it("separates due, future, missed and finished sessions without declaring a partial task complete", () => {
  expect(itemStatus(item(10, 20), 5)).toBe("Upcoming");
  expect(itemStatus(item(10, 20), 10)).toBe("Ready now");
  expect(itemStatus(item(10, 20), 20)).toBe("Pick up again");
  expect(itemStatus({ ...item(10, 20), state: "completed" }, 30)).toBe("Session finished");
});
