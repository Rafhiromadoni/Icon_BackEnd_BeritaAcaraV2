package com.rafhi.dto;

import jakarta.ws.rs.FormParam;
import org.jboss.resteasy.annotations.providers.multipart.PartType;
import java.io.InputStream;

public class TemplateUploadForm {
    @FormParam("file")
    @PartType("application/octet-stream")
    public InputStream file;

    @FormParam("fileName")
    @PartType("text/plain")
    public String fileName;
}
