package com.atlas.backend.ingestion;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class StructuredTextParser implements DocumentParser {
    private static final Pattern HEADING=Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern LIST=Pattern.compile("^(\\s*)(?:[-*+] |\\d+[.)] )(.+)$");
    private static final Pattern LINK=Pattern.compile("https?://[^\\s)<>]+");
    private static final Set<String> TYPES=Set.of("task","resource","optional","prerequisite","note","milestone","project");
    private record Parent(int depth,int id,String type) {}
    @Override public List<ImportNode> parse(String text) {
        List<ImportNode> nodes=new ArrayList<>();Deque<Parent> headings=new ArrayDeque<>(),lists=new ArrayDeque<>();
        boolean code=false;
        for(String raw:text.replace("\r\n","\n").replace('\r','\n').split("\n",-1)) {
            if(raw.isBlank())continue;
            if(raw.stripLeading().startsWith("```")){code=!code;continue;}
            var heading=HEADING.matcher(raw);var bullet=LIST.matcher(raw);
            String title=raw.strip();String type="note";Integer parent=headings.isEmpty()?null:headings.peek().id();int depth=-1;
            boolean isHeading=!code && heading.matches(),isList=!code && bullet.matches();
            if(isHeading){depth=heading.group(1).length();title=heading.group(2).strip();while(!headings.isEmpty() && headings.peek().depth()>=depth)headings.pop();parent=headings.isEmpty()?null:headings.peek().id();lists.clear();type="milestone";}
            else if(isList){depth=bullet.group(1).replace("\t","    ").length();title=bullet.group(2).strip().replaceFirst("^\\[[ xX]\\]\\s*","");while(!lists.isEmpty() && lists.peek().depth()>=depth)lists.pop();parent=lists.isEmpty()?parent:lists.peek().id();type="task";}
            else lists.clear();
            String lower=title.toLowerCase(Locale.ROOT);
            if(!code){
                int colon=lower.indexOf(':');
                if(colon>0 && TYPES.contains(lower.substring(0,colon))){type=lower.substring(0,colon);title=title.substring(colon+1).strip();}
                else if(lower.startsWith("optional") || lower.startsWith("if time permits"))type="optional";
                else if(lower.startsWith("prerequisite"))type="prerequisite";
                else if(!isHeading && (LINK.matcher(title).find() || lower.matches("^(read|watch)\\b.*")))type="resource";
                else if(!isHeading && lower.matches("^(implement|write|submit|build|complete|practice)\\b.*"))type="task";
                else if(!isHeading && parent!=null && nodes.get(parent-1).type().equals("optional") && type.equals("task"))type="optional";
            }
            if(title.length()>255)title=title.substring(0,255);
            var url=LINK.matcher(raw);String reference=url.find()?url.group():null;
            int id=nodes.size()+1;
            nodes.add(new ImportNode(id,parent,type,title,raw,!Set.of("optional","prerequisite").contains(type),null,null,null,
                type.equals("resource")?"link":null,reference,null,null,null,type.equals("note")?"Unclassified text retained for review":null));
            if(isHeading)headings.push(new Parent(depth,id,type));else if(isList)lists.push(new Parent(depth,id,type));
            if(nodes.size()>500)throw new IllegalArgumentException("Document exceeds 500 review nodes. Split it into smaller imports.");
        }
        return List.copyOf(nodes);
    }
}
