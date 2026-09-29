package com.atlas.backend.resource;

import com.atlas.backend.event.*;
import com.atlas.backend.execution.*;
import com.atlas.backend.scheduling.SchedulingConfigRepository;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import tools.jackson.databind.JsonNode;

@Service
public class ResourceService {
    public record Input(String type, String title, String urlOrFileRef) {
        @JsonAnySetter public void unknown(String key, JsonNode value) { throw new IllegalArgumentException("Unknown resource field"); }
    }
    public record Resource(long id, String type, String title, String urlOrFileRef, String addedBy) {}
    public record Feedback(String reaction, String comment) {
        @JsonAnySetter public void unknown(String key, JsonNode value) { throw new IllegalArgumentException("Unknown feedback field"); }
    }
    private final JdbcTemplate db;
    private final SchedulingConfigRepository config;
    private final ExecutionIdempotency replay;
    private final EventRepository events;
    private final ExecutionClock clock;
    private final ObjectMapper mapper=new ObjectMapper();
    public ResourceService(JdbcTemplate db, SchedulingConfigRepository config, ExecutionIdempotency replay, EventRepository events, ExecutionClock clock) {
        this.db=db; this.config=config; this.replay=replay; this.events=events; this.clock=clock;
    }
    public static void validate(Input input) {
        if(input==null || input.type()==null || !Set.of("video","pdf","doc","link","book","course").contains(input.type())
            || input.title()==null || input.title().isBlank() || input.title().length()>255
            || input.urlOrFileRef()==null || input.urlOrFileRef().isBlank() || input.urlOrFileRef().length()>2048)
            throw new IllegalArgumentException("Provide a resource type, title and reference.");
        // References are labels or HTTP(S) URLs, never paths that the server reads.
        String ref=input.urlOrFileRef();
        if(ref.contains(":") && !(ref.startsWith("https://") || ref.startsWith("http://")))
            throw new IllegalArgumentException("Use an HTTP(S) URL or a book/document reference label.");
        if(ref.startsWith("http")) {
            try { var uri=URI.create(ref); if(uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException(); }
            catch(IllegalArgumentException e) { throw new IllegalArgumentException("Provide a valid HTTP(S) URL."); }
        }
    }
    @Transactional(readOnly=true) public List<Resource> list(long owner) {
        return db.query("SELECT * FROM resources WHERE user_id=? ORDER BY id",(r,n)->new Resource(r.getLong("id"),r.getString("type"),r.getString("title"),r.getString("url_or_file_ref"),r.getString("added_by")),owner);
    }
    @Transactional(readOnly=true) public Resource get(long owner,long id) {
        return db.query("SELECT * FROM resources WHERE user_id=? AND id=?",(r,n)->new Resource(r.getLong("id"),r.getString("type"),r.getString("title"),r.getString("url_or_file_ref"),r.getString("added_by")),owner,id).stream().findFirst().orElseThrow(()->new ExecutionException(404,"Resource not found"));
    }
    @Transactional public Resource createImported(long owner,Input input) {
        config.lockOwner(owner); validate(input);
        var keys=new GeneratedKeyHolder();
        db.update(c->{var p=c.prepareStatement("INSERT INTO resources(user_id,type,title,url_or_file_ref,added_by) VALUES(?,?,?,?,'user')",java.sql.Statement.RETURN_GENERATED_KEYS);
            p.setLong(1,owner);p.setString(2,input.type());p.setString(3,input.title());p.setString(4,input.urlOrFileRef());return p;},keys);
        var value=get(owner,keys.getKey().longValue()); event(value.id(),"resource.created",value); return value;
    }
    @Transactional public String create(long owner,String key,Input input) {
        config.lockOwner(owner); String fp="resource:create:"+mapper.writeValueAsString(input), old=replay.replay(owner,key,fp);
        if(old!=null)return old;
        return replay.save(owner,key,fp,mapper.writeValueAsString(createImported(owner,input)));
    }
    @Transactional public String update(long owner,long id,String key,Input input) {
        config.lockOwner(owner); String fp="resource:update:"+id+":"+mapper.writeValueAsString(input),old=replay.replay(owner,key,fp);if(old!=null)return old;
        get(owner,id);validate(input);
        db.update("UPDATE resources SET type=?,title=?,url_or_file_ref=? WHERE user_id=? AND id=?",input.type(),input.title(),input.urlOrFileRef(),owner,id);
        var value=get(owner,id);event(id,"resource.updated",value);
        return replay.save(owner,key,fp,mapper.writeValueAsString(value));
    }
    @Transactional public String delete(long owner,long id,String key) {
        config.lockOwner(owner);String fp="resource:delete:"+id,old=replay.replay(owner,key,fp);if(old!=null)return old;
        var value=get(owner,id);
        if(db.queryForObject("SELECT COUNT(*) FROM task_resource WHERE resource_id=?",Long.class,id)>0 || db.queryForObject("SELECT COUNT(*) FROM resource_feedback WHERE resource_id=?",Long.class,id)>0)
            throw new ExecutionException(409,"Detach this resource first. Resources with feedback are retained as history.");
        db.update("DELETE FROM resources WHERE id=? AND user_id=?",id,owner);event(id,"resource.deleted",value);
        return replay.save(owner,key,fp,"{}");
    }
    private void task(long owner,long task) {
        if(db.queryForObject("SELECT COUNT(*) FROM commitments WHERE user_id=? AND id=?",Long.class,owner,task)!=1) throw new ExecutionException(404,"Commitment not found");
    }
    @Transactional(readOnly=true) public List<Resource> attachments(long owner,long task) {
        task(owner,task);
        return db.query("SELECT r.* FROM resources r JOIN task_resource t ON t.resource_id=r.id AND t.user_id=r.user_id WHERE t.user_id=? AND t.commitment_id=? ORDER BY r.id",(r,n)->new Resource(r.getLong("id"),r.getString("type"),r.getString("title"),r.getString("url_or_file_ref"),r.getString("added_by")),owner,task);
    }
    @Transactional public void attachImported(long owner,long task,long resource) {
        config.lockOwner(owner);task(owner,task);get(owner,resource);
        if(db.queryForObject("SELECT COUNT(*) FROM task_resource WHERE commitment_id=? AND resource_id=?",Long.class,task,resource)==0) {
            db.update("INSERT INTO task_resource(user_id,commitment_id,resource_id) VALUES(?,?,?)",owner,task,resource);
            event(resource,"resource.attached",Map.of("commitmentId",task,"resourceId",resource));
        }
    }
    @Transactional public String attachment(long owner,long task,long resource,Long replacement,boolean detach,String key) {
        config.lockOwner(owner);String fp="attachment:"+task+":"+resource+":"+replacement+":"+detach,old=replay.replay(owner,key,fp);if(old!=null)return old;
        task(owner,task);get(owner,resource);
        if(replacement!=null) get(owner,replacement);
        if(detach || replacement!=null) {
            if(replacement!=null && db.queryForObject("SELECT COUNT(*) FROM task_resource WHERE user_id=? AND commitment_id=? AND resource_id=?",Long.class,owner,task,resource)==0)
                throw new ExecutionException(404,"Attachment not found");
            if(db.update("DELETE FROM task_resource WHERE user_id=? AND commitment_id=? AND resource_id=?",owner,task,resource)>0)
                event(resource,"resource.detached",Map.of("commitmentId",task,"resourceId",resource));
        }
        if(!detach) attachImported(owner,task,replacement==null?resource:replacement);
        return replay.save(owner,key,fp,mapper.writeValueAsString(attachments(owner,task)));
    }
    @Transactional public String feedback(long owner,long id,String key,Feedback input) {
        config.lockOwner(owner);String fp="feedback:"+id+":"+mapper.writeValueAsString(input),old=replay.replay(owner,key,fp);if(old!=null)return old;
        get(owner,id);
        if(input==null || input.reaction()==null || !Set.of("liked","disliked").contains(input.reaction()) || (input.comment()!=null && input.comment().length()>8000)) throw new IllegalArgumentException("Choose liked or disliked; comment maximum is 8000 characters.");
        db.update("INSERT INTO resource_feedback(user_id,resource_id,reaction,comment,created_at) VALUES(?,?,?,?,?)",owner,id,input.reaction(),input.comment(),LocalDateTime.ofInstant(clock.now(),ZoneOffset.UTC));
        event(id,"resource.feedback_recorded",input);
        return replay.save(owner,key,fp,mapper.writeValueAsString(Map.of("tier","single_reaction","reaction",input.reaction(),"preferenceCreated",false)));
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> feedbackHistory(long owner,long id) {
        get(owner,id); return db.query("SELECT id,reaction,comment,created_at FROM resource_feedback WHERE user_id=? AND resource_id=? ORDER BY id",(r,n)->{
            Map<String,Object> row=new LinkedHashMap<>();row.put("id",r.getLong(1));row.put("reaction",r.getString(2));row.put("comment",r.getString(3));row.put("createdAt",r.getObject(4,LocalDateTime.class).toInstant(ZoneOffset.UTC).toString());return row;},owner,id);
    }
    private void event(long id,String type,Object payload) {events.appendAndFlush(Event.forEntity("resource",id,type,"user",null,mapper.writeValueAsString(payload)));}
}
