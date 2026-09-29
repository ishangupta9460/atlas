package com.atlas.backend.ingestion;

import com.atlas.backend.commitment.*;
import com.atlas.backend.roadmap.*;
import com.atlas.backend.resource.ResourceService;
import com.atlas.backend.fixedcommitment.*;
import com.atlas.backend.event.*;
import com.atlas.backend.execution.*;
import com.atlas.backend.scheduling.SchedulingConfigRepository;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.nio.*;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.*;

@Service
public class ImportService {
    public record Proposal(List<ImportNode> nodes,String warning) {}
    public record Edit(int revision,List<ImportNode> nodes) {
        @JsonAnySetter public void unknown(String key,JsonNode value){throw new IllegalArgumentException("Unknown import field");}
    }
    public record Approval(int revision,Long goalId,String importance,String flexibilityTier) {
        @JsonAnySetter public void unknown(String key,JsonNode value){throw new IllegalArgumentException("Unknown approval field");}
    }
    public record Result(Long roadmapId,Long goalId,List<Long> commitmentIds,List<Long> resourceIds,List<Long> fixedCommitmentIds,List<Long> conflictingBlockIds) {}
    public record View(long id,String kind,String filename,String state,int revision,Proposal proposal,Result result,int scheduledCount) {}
    private final JdbcTemplate db; private final SchedulingConfigRepository config;private final ExecutionIdempotency replay;private final EventRepository events;
    private final DocumentParser parser;private final FixedScheduleParser fixedParser;private final ScreenshotExtractor ocr;
    private final CommitmentService tasks;private final RoadmapService roadmaps;private final ResourceService resources;private final FixedCommitmentService fixed;
    private final ObjectMapper mapper=new ObjectMapper();
    public ImportService(JdbcTemplate db,SchedulingConfigRepository config,ExecutionIdempotency replay,EventRepository events,DocumentParser parser,
                         FixedScheduleParser fixedParser,ScreenshotExtractor ocr,CommitmentService tasks,RoadmapService roadmaps,ResourceService resources,FixedCommitmentService fixed){
        this.db=db;this.config=config;this.replay=replay;this.events=events;this.parser=parser;this.fixedParser=fixedParser;this.ocr=ocr;this.tasks=tasks;this.roadmaps=roadmaps;this.resources=resources;this.fixed=fixed;
    }
    @Transactional(readOnly=true) public View get(long owner,long id,String kind){
        var value=db.query("SELECT * FROM import_proposals WHERE user_id=? AND id=? AND kind=?",(r,n)->new View(r.getLong("id"),r.getString("kind"),r.getString("filename"),r.getString("state"),r.getInt("revision"),mapper.readValue(r.getString("proposal_json"),Proposal.class),r.getString("result_json")==null?null:mapper.readValue(r.getString("result_json"),Result.class),0),owner,id,kind).stream().findFirst().orElseThrow(()->new ExecutionException(404,"Import not found"));
        int count=0;if(value.result()!=null)for(long task:value.result().commitmentIds())count+=db.queryForObject("SELECT COUNT(*) FROM scheduled_blocks WHERE user_id=? AND commitment_id=? AND state IN ('scheduled','active')",Integer.class,owner,task);
        return new View(value.id(),kind,value.filename(),value.state(),value.revision(),value.proposal(),value.result(),count);
    }
    @Transactional(readOnly=true) public List<View> list(long owner,String kind){return db.query("SELECT id FROM import_proposals WHERE user_id=? AND kind=? ORDER BY id DESC",(r,n)->r.getLong(1),owner,kind).stream().map(id->get(owner,id,kind)).toList();}
    @Transactional public String upload(long owner,String kind,String key,String filename,String media,byte[] content,String transcript){
        if(!Set.of("roadmap","fixed").contains(kind))throw new IllegalArgumentException("Invalid import kind");
        if(filename==null || filename.isBlank() || filename.length()>255 || filename.contains("/") || filename.contains("\\") || filename.contains(":") || filename.chars().anyMatch(c->c<32))throw new IllegalArgumentException("Provide a plain filename without a path.");
        if(content==null || content.length==0 || content.length>(kind.equals("fixed")?2*1024*1024:256*1024))throw new IllegalArgumentException("Upload is empty or exceeds the size limit (text 256 KiB; image 2 MiB).");
        if(transcript!=null && transcript.length()>262144)throw new IllegalArgumentException("Transcript exceeds the text limit.");
        config.lockOwner(owner);String fp="import:"+kind+":"+filename+":"+media+":"+hash(content)+":"+hash((transcript==null?"":transcript).getBytes(StandardCharsets.UTF_8));String old=replay.replay(owner,key,fp);if(old!=null)return old;
        Proposal proposal;
        String name=filename.toLowerCase(Locale.ROOT);
        if(kind.equals("roadmap")) {
            if(!(name.endsWith(".md")||name.endsWith(".txt")||name.endsWith(".markdown")) || !Set.of("text/plain","text/markdown","application/octet-stream").contains(media==null?"":media))throw new ExecutionException(415,"First-pass import supports Markdown and UTF-8 structured text. PDF/Word and roadmap images are deferred.");
            String text;try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();}catch(CharacterCodingException e){throw new IllegalArgumentException("Document must contain valid UTF-8 text.");}
            if(text.chars().anyMatch(c->c==0 || (c<32 && c!='\n' && c!='\r' && c!='\t')))throw new IllegalArgumentException("Document contains binary/control data.");
            proposal=new Proposal(parser.parse(text),text.isBlank()?"No content found. Upload a nonempty document.":null);
        }else{
            validateImage(content,media,name);
            var extraction=transcript==null || transcript.isBlank()?ocr.extract(content):new ScreenshotExtractor.Extraction(transcript,"User-supplied transcript; verify it against the screenshot.");
            proposal=new Proposal(fixedParser.parse(extraction.text()),extraction.warning());
        }
        validateNodes(kind,proposal.nodes());
        var keys=new GeneratedKeyHolder();String json=mapper.writeValueAsString(proposal);
        db.update(c->{var p=c.prepareStatement("INSERT INTO import_proposals(user_id,kind,filename,media_type,upload_bytes,proposal_json) VALUES(?,?,?,?,?,?)",new String[]{"id"});p.setLong(1,owner);p.setString(2,kind);p.setString(3,filename);p.setString(4,media==null?"application/octet-stream":media);p.setBytes(5,content);p.setString(6,json);return p;},keys);
        long id=keys.getKey().longValue();event(id,"import.uploaded",Map.of("kind",kind,"nodeCount",proposal.nodes().size()));
        return replay.save(owner,key,fp,mapper.writeValueAsString(get(owner,id,kind)));
    }
    private void validateImage(byte[] bytes,String media,String name){
        boolean png="image/png".equals(media) && name.endsWith(".png"),jpeg="image/jpeg".equals(media) && (name.endsWith(".jpg")||name.endsWith(".jpeg"));
        if(!png && !jpeg)throw new ExecutionException(415,"Upload a PNG or JPEG screenshot.");
        if(png && (bytes.length<8 || bytes[0]!=(byte)137 || bytes[1]!=80 || bytes[2]!=78 || bytes[3]!=71) || jpeg && (bytes.length<3 || bytes[0]!=(byte)255 || bytes[1]!=(byte)216))throw new IllegalArgumentException("Image signature does not match its type.");
        try(var input=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))){
            var readers=javax.imageio.ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException("Malformed screenshot.");var reader=readers.next();
            try {reader.setInput(input);if((long)reader.getWidth(0)*reader.getHeight(0)>16_000_000)throw new IllegalArgumentException("Screenshot exceeds 16 megapixels.");if(reader.read(0)==null)throw new IllegalArgumentException("Malformed screenshot.");}finally{reader.dispose();}
        }catch(java.io.IOException e){throw new IllegalArgumentException("Malformed screenshot.");}
    }
    @Transactional public String edit(long owner,long id,String kind,String key,Edit edit){
        config.lockOwner(owner);String fp="import:edit:"+kind+":"+id+":"+mapper.writeValueAsString(edit),old=replay.replay(owner,key,fp);if(old!=null)return old;
        var view=get(owner,id,kind);review(view,edit.revision());validateNodes(kind,edit.nodes());
        for(var node:edit.nodes())if(node.resourceId()!=null)resources.get(owner,node.resourceId());
        db.update("UPDATE import_proposals SET proposal_json=?,revision=revision+1 WHERE id=? AND user_id=?",mapper.writeValueAsString(new Proposal(edit.nodes(),view.proposal().warning())),id,owner);
        event(id,"import.reviewed",Map.of("revision",edit.revision()+1));return replay.save(owner,key,fp,mapper.writeValueAsString(get(owner,id,kind)));
    }
    private void review(View view,int revision){if(!view.state().equals("review"))throw new ExecutionException(409,"Import is already approved.");if(view.revision()!=revision)throw new ExecutionException(409,"Review changed. Reload before approving or editing.");}
    private void validateNodes(String kind,List<ImportNode> nodes){
        if(nodes==null || nodes.size()>500)throw new IllegalArgumentException("Provide at most 500 nodes.");Set<Integer> ids=new HashSet<>();
        for(var n:nodes){
            if(n==null || n.id()<=0 || ids.contains(n.id()) || n.parentId()!=null && !ids.contains(n.parentId()) || n.title()==null || n.title().isBlank() || n.title().length()>255
                || n.type()==null || !(kind.equals("fixed")?Set.of("fixed"):Set.of("task","resource","optional","prerequisite","note","milestone","project")).contains(n.type()))throw new IllegalArgumentException("Nodes need unique IDs, earlier parents, valid types and titles (up to 255 characters).");
            for(String text:List.of(n.text()==null?"":n.text(),n.completionCriterion()==null?"":n.completionCriterion()))if(text.length()>8000)throw new IllegalArgumentException("Node text exceeds 8000 characters.");
            ids.add(n.id());
        }
    }
    @Transactional public String approve(long owner,long id,String kind,String key,Approval input){
        config.lockOwner(owner);String fp="import:approve:"+kind+":"+id+":"+mapper.writeValueAsString(input),old=replay.replay(owner,key,fp);if(old!=null)return old;
        var view=get(owner,id,kind);review(view,input.revision());
        var nodes=view.proposal().nodes();Set<Integer> excluded=new HashSet<>();List<ImportNode> selected=new ArrayList<>();
        for(var node:nodes){if(!node.included() || node.parentId()!=null && excluded.contains(node.parentId()))excluded.add(node.id());else selected.add(node);}
        if(selected.isEmpty())throw new IllegalArgumentException("Select at least one node before approval.");
        List<Long> taskIds=new ArrayList<>(),resourceIds=new ArrayList<>(),fixedIds=new ArrayList<>();Set<Long> conflicts=new TreeSet<>();Long roadmapId=null;
        Map<Integer,Long> milestoneByNode=new HashMap<>(),taskByNode=new HashMap<>();Map<Integer,ImportNode> byId=new HashMap<>();nodes.forEach(n->byId.put(n.id(),n));
        if(kind.equals("roadmap")){
            if(input.goalId()==null)throw new IllegalArgumentException("Choose the Goal for this roadmap.");
            roadmapId=roadmaps.createRoadmap(owner,input.goalId(),new CreateRoadmapRequest("imported")).id();
            event(id,"import.roadmap_created",Map.of("roadmapId",roadmapId));
            for(var n:selected)if(n.type().equals("milestone"))milestoneByNode.put(n.id(),roadmaps.createMilestone(owner,roadmapId,new CreateMilestoneRequest(n.title(),milestoneByNode.size())).id());
            for(var n:selected)if(Set.of("task","project","optional","prerequisite").contains(n.type())){
                Map<String,Object> fields=new LinkedHashMap<>();fields.put("title",n.title());fields.put("completionCriterion",n.completionCriterion());fields.put("goalId",input.goalId());
                fields.put("milestoneId",ancestor(n,byId,milestoneByNode));fields.put("importance",n.importance()==null?input.importance():n.importance());fields.put("flexibilityTier",n.type().equals("optional")?"optional":n.flexibilityTier()==null?input.flexibilityTier():n.flexibilityTier());
                String notes=selected.stream().filter(x->x.type().equals("note") && Objects.equals(x.parentId(),n.id())).map(ImportNode::text).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining("\n"));
                fields.put("description",notes.isEmpty()?n.text():notes);
                long task=tasks.create(owner,mapper.readValue(mapper.writeValueAsString(fields),CommitmentRequest.class)).id();taskIds.add(task);taskByNode.put(n.id(),task);
            }
            for(var n:selected)if(n.type().equals("resource")){
                long resource=n.resourceId()==null?resources.createImported(owner,new ResourceService.Input(n.resourceType(),n.title(),n.reference())).id():resources.get(owner,n.resourceId()).id();resourceIds.add(resource);
                Long parentTask=ancestor(n,byId,taskByNode);if(parentTask!=null)resources.attachImported(owner,parentTask,resource);
            }
        }else for(var n:selected){
            var created=fixed.createFromApprovedImport(owner,n.title(),n.startTime(),n.endTime());fixedIds.add(created.id());
            conflicts.addAll(db.query("SELECT id FROM scheduled_blocks WHERE user_id=? AND state IN ('scheduled','active') AND start_time<? AND end_time>? ORDER BY id",(r,i)->r.getLong(1),owner,LocalDateTime.ofInstant(created.endTime(),ZoneOffset.UTC),LocalDateTime.ofInstant(created.startTime(),ZoneOffset.UTC)));
        }
        var result=new Result(roadmapId,kind.equals("roadmap")?input.goalId():null,List.copyOf(taskIds),List.copyOf(resourceIds),List.copyOf(fixedIds),List.copyOf(conflicts));
        db.update("UPDATE import_proposals SET state='approved',result_json=? WHERE id=? AND user_id=?",mapper.writeValueAsString(result),id,owner);
        event(id,"import.approved",result);return replay.save(owner,key,fp,mapper.writeValueAsString(get(owner,id,kind)));
    }
    private Long ancestor(ImportNode node,Map<Integer,ImportNode> nodes,Map<Integer,Long> entities){Integer parent=node.parentId();while(parent!=null){if(entities.containsKey(parent))return entities.get(parent);parent=nodes.get(parent).parentId();}return null;}
    private void event(long id,String type,Object payload){events.appendAndFlush(Event.forEntity("import_proposal",id,type,"user",null,mapper.writeValueAsString(payload)));}
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
