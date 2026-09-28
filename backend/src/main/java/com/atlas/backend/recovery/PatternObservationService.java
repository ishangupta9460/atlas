package com.atlas.backend.recovery;

import com.atlas.backend.execution.ExecutionClock;
import com.atlas.backend.scheduling.SchedulingConfigRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatternObservationService {
    public record Finding(Long categoryId,int localHour,PatternDetector.Observation observation) {}
    public record Result(boolean configured,List<Finding> observations) {}
    private final PatternDetector.Policy policy;
    private final ContextualCompletionEvidence evidence;
    private final SchedulingConfigRepository config;
    private final ExecutionClock clock;
    public PatternObservationService(ContextualCompletionEvidence evidence,SchedulingConfigRepository config,ExecutionClock clock,
        @Value("${atlas.recovery.pattern.minimum-weeks:}") String weeks,@Value("${atlas.recovery.pattern.minimum-instances:}") String samples,
        @Value("${atlas.recovery.pattern.missed-ratio:}") String ratio) {
        this.evidence=evidence;this.config=config;this.clock=clock;
        if(weeks.isBlank() && samples.isBlank() && ratio.isBlank()) policy=null;
        else policy=new PatternDetector.Policy(Integer.parseInt(weeks),Integer.parseInt(samples),new BigDecimal(ratio));
    }
    @Transactional(readOnly=true)
    public Result observations(Long owner) {
        if(policy==null) return new Result(false,List.of());
        var hours=config.workingHours(owner);if(hours.isEmpty()) return new Result(true,List.of());
        var zone=ZoneId.of(hours.get().timezone());
        record Context(Long category,int hour) {}
        var groups=new LinkedHashMap<Context,List<PatternDetector.Evidence>>();
        for(var f:evidence.read(owner,clock.now())) {
            var local=f.start().atZone(zone);var context=new Context(f.categoryId(),local.getHour());
            groups.computeIfAbsent(context,k->new ArrayList<>()).add(new PatternDetector.Evidence(local.toLocalDate(),f.completed()));
        }
        var results=new ArrayList<Finding>();
        groups.forEach((context,facts)->PatternDetector.detect(facts,policy).ifPresent(o->results.add(new Finding(context.category(),context.hour(),o))));
        results.sort(Comparator.comparing(Finding::categoryId,Comparator.nullsFirst(Comparator.naturalOrder())).thenComparingInt(Finding::localHour));
        return new Result(true,List.copyOf(results));
    }
}
