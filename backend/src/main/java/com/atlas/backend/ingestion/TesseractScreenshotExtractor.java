package com.atlas.backend.ingestion;

import java.nio.file.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Optional local OCR adapter; never invokes a shell or transmits the user's image. */
@Component
public class TesseractScreenshotExtractor implements ScreenshotExtractor {
    private final String executable;
    public TesseractScreenshotExtractor(@Value("${atlas.import.ocr-executable:tesseract}") String executable){this.executable=executable;}
    public Extraction extract(byte[] image) {
        Path directory=null;Process process=null;
        try {
            directory=Files.createTempDirectory("atlas-ocr-");Path input=directory.resolve("input.png"),output=directory.resolve("output");
            Files.write(input,image);
            process=new ProcessBuilder(executable,input.toString(),output.toString(),"--psm","6")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if(!process.waitFor(Duration.ofSeconds(20).toMillis(),TimeUnit.MILLISECONDS)){process.destroyForcibly().waitFor();return new Extraction("","OCR timed out. Enter the schedule entries manually for review.");}
            Path result=directory.resolve("output.txt");
            if(process.exitValue()!=0 || !Files.exists(result) || Files.size(result)>262144)return new Extraction("","OCR could not read this image. Enter the schedule entries manually for review.");
            return new Extraction(Files.readString(result),"Verify all extracted dates and times before approving.");
        } catch(java.io.IOException e){return new Extraction("","Local OCR is unavailable. Enter the schedule entries manually for review.");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Screenshot extraction interrupted");}
        finally {
            if(process!=null && process.isAlive())process.destroyForcibly();
            if(directory!=null)for(String name:new String[]{"input.png","output.txt",""})try{Files.deleteIfExists(name.isEmpty()?directory:directory.resolve(name));}catch(java.io.IOException ignored){/* Best-effort removal of server-generated temporary files only. */}
        }
    }
}
