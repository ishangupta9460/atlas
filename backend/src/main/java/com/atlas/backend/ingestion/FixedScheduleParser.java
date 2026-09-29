package com.atlas.backend.ingestion;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.stereotype.Component;

/** Never guesses a date, timezone or recurrence from a weekday/time label. */
@Component
public class FixedScheduleParser {
    public List<ImportNode> parse(String text) {
        List<ImportNode> nodes=new ArrayList<>();
        for(String line:text.split("\\R")) {
            if(line.isBlank())continue;
            String[] parts=line.split("\\|",-1);String title=parts[0].strip(),start=null,end=null,warning="Enter explicit dates and times with UTC offsets; weekday-only entries are ambiguous.";
            if(parts.length==3)try {
                start=OffsetDateTime.parse(parts[1].strip()).toInstant().toString();end=OffsetDateTime.parse(parts[2].strip()).toInstant().toString();
                if(!java.time.Instant.parse(end).isAfter(java.time.Instant.parse(start)))throw new IllegalArgumentException();warning=null;
            }catch(RuntimeException e){start=null;end=null;}
            nodes.add(new ImportNode(nodes.size()+1,null,"fixed",title.length()>255?title.substring(0,255):title,line,true,null,null,null,null,null,null,start,end,warning));
            if(nodes.size()>500)throw new IllegalArgumentException("Screenshot exceeds 500 entries.");
        }
        return List.copyOf(nodes);
    }
}
