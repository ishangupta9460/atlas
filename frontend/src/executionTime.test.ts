import { describe, expect, it } from "vitest";
import { dateKey, dayStart, localInput } from "./executionTime";
import { dayBounds } from "./dayTimeline";

describe("saved scheduling timezone", () => {
  it("groups dates in the saved zone, independently of the browser", () => {
    expect(dateKey(Date.parse("2026-09-26T20:00:00Z"), "Asia/Kolkata")).toBe("2026-09-27");
    expect(new Date(dayStart("2026-09-27", "Asia/Kolkata")).toISOString()).toBe("2026-09-26T18:30:00.000Z");
    expect(new Date(localInput("2026-09-27T10:00", "Asia/Kolkata")).toISOString()).toBe("2026-09-27T04:30:00.000Z");
  });
  it("preserves short and long DST days and refuses ambiguous manual input", () => {
    const spring=dayBounds(Date.parse("2026-03-08T12:00:00Z"), "America/New_York");
    const fall=dayBounds(Date.parse("2026-11-01T12:00:00Z"), "America/New_York");
    expect(spring.end-spring.start).toBe(23*3600000);
    expect(fall.end-fall.start).toBe(25*3600000);
    expect(localInput("2026-03-08T02:30", "America/New_York")).toBeNaN();
    expect(localInput("2026-11-01T01:30", "America/New_York")).toBeNaN();
  });
});
