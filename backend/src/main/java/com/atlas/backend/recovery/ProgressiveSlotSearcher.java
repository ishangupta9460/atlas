package com.atlas.backend.recovery;

import com.atlas.backend.scheduling.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ProgressiveSlotSearcher {
    private final SchedulingPipeline pipeline;
    public ProgressiveSlotSearcher(SchedulingPipeline pipeline) { this.pipeline=pipeline; }
    public SchedulingPlanner.Plan search(Long owner, Instant now, ZoneId zone, List<SchedulingPipeline.WorkInput> work,
                                         Set<Long> excludedBlocks, List<TimeInterval> unavailable) {
        var remaining=new TreeMap<Long,SchedulingPipeline.WorkInput>(); work.forEach(w->remaining.put(w.commitmentId(),w));
        var placements=new ArrayList<SchedulingPlanner.Placement>();
        var ties=new ArrayList<SchedulingPlanner.Tie>();
        var reserved=new ArrayList<>(unavailable);
        for(var window:windows(now,zone)) {
            if(remaining.isEmpty()) break;
            var plan=pipeline.preview(owner,new SchedulingPipeline.Request(window.start(),window.end(),List.copyOf(remaining.values()),null),excludedBlocks,reserved);
            placements.addAll(plan.placements()); ties.addAll(plan.importantTies());
            for(var p:plan.placements()) { remaining.remove(p.commitmentId()); reserved.add(new TimeInterval(p.startTime(),p.endTime())); }
        }
        return new SchedulingPlanner.Plan(List.copyOf(placements),List.copyOf(remaining.keySet()),List.copyOf(ties));
    }
    public static List<TimeInterval> windows(Instant now, ZoneId zone) {
        LocalDate today=now.atZone(zone).toLocalDate();
        Instant tomorrow=today.plusDays(1).atStartOfDay(zone).toInstant();
        Instant afterTomorrow=today.plusDays(2).atStartOfDay(zone).toInstant();
        Instant weekEnd=today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant();
        var windows=new ArrayList<TimeInterval>();
        if(tomorrow.isAfter(now)) windows.add(new TimeInterval(now,tomorrow));
        if(afterTomorrow.isAfter(tomorrow)) windows.add(new TimeInterval(tomorrow,afterTomorrow));
        if(weekEnd.isAfter(afterTomorrow)) windows.add(new TimeInterval(afterTomorrow,weekEnd));
        return List.copyOf(windows);
    }
}
