package com.rafhi.controller;

import com.rafhi.dto.BeritaAcaraRequest;
import com.rafhi.dto.Fitur;
import com.rafhi.dto.HistoryResponseDTO;
import com.rafhi.dto.Signatory;
import com.rafhi.entity.BeritaAcaraHistory;
import com.rafhi.helper.DateToWordsHelper;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import org.apache.poi.xwpf.usermodel.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Path("/berita-acara")
@ApplicationScoped
@Authenticated
public class BeritaAcaraResource {

    @Inject
    Jsonb jsonb; 

    @Inject
    SecurityIdentity securityIdentity; 

    @POST
    @Path("/generate-docx")
    @Produces("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    @Consumes("application/json")
    @Transactional 
    public Response generateDocx(BeritaAcaraRequest request) throws Exception {
        String templateFileName;

        if ("Deployment".equalsIgnoreCase(request.jenisBeritaAcara)) {
            templateFileName = "template_deploy.docx";
        } else { // Asumsi jenisnya adalah UAT
            // Hitung jumlah penandatangan dengan tipe "utama" 
            long countUtama = request.signatoryList.stream().filter(s -> s.tipe.startsWith("penandatangan")).count();
                
            if (countUtama == 3) {
                templateFileName = "template_uat_signatory4.docx"; 
            } else if (countUtama == 4) {
                templateFileName = "template_uat_signatory5.docx";
            } else { // default jml 2
                templateFileName = "template_uat.docx";
            }
        }

        String templatePath = "/templates/" + templateFileName;
        InputStream templateInputStream = getClass().getResourceAsStream(templatePath);

        if (templateInputStream == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("Template file not found at path: " + templatePath)
                           .build();
        }

        try (XWPFDocument document = new XWPFDocument(templateInputStream)) {
            Map<String, String> replacements = buildReplacementsMap(request);

            replaceTextPlaceholders(document, replacements);

            if ("UAT".equalsIgnoreCase(request.jenisBeritaAcara) && request.fiturList != null && !request.fiturList.isEmpty()) {
                Fitur fitur = request.fiturList.get(0); // Mengambil fitur pertama
                replacePlaceholderWithHtml(document, "${fitur.deskripsi}", fitur.deskripsi);
            }
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);

            byte[] docxBytes = out.toByteArray();
            BeritaAcaraHistory history = new BeritaAcaraHistory();

            history.username = securityIdentity.getPrincipal().getName(); // Simpan username yang membuat berita acara
            history.nomorBA = request.nomorBA;
            history.jenisBeritaAcara = request.jenisBeritaAcara;
            history.judulPekerjaan = request.judulPekerjaan;
            history.generationTimestamp = LocalDateTime.now();
            history.requestJson = jsonb.toJson(request); // Ubah request menjadi string JSON
            history.fileContent = docxBytes; // Simpan file

            history.persistAndFlush(); 

            ResponseBuilder response = Response.ok(new ByteArrayInputStream(docxBytes));
            response.header("Content-Disposition", "inline; filename=BA-" + request.nomorBA + ".docx");
            response.header("X-History-ID", history.id); // Tambahkan header ini
            response.header("Access-Control-Expose-Headers", "X-History-ID"); 
            return response.build();
        }
    }

    @GET
    @Path("/history")
    @Produces("application/json")
    @Transactional // Pastikan ini juga dalam konteks transaksi
    public Response getHistory() {
        String currentUsername = securityIdentity.getPrincipal().getName();
        
        List<BeritaAcaraHistory> historyList = BeritaAcaraHistory.find("username", currentUsername).list();
        
        List<HistoryResponseDTO> responseList = historyList.stream()
            .map(h -> new HistoryResponseDTO(h.id, h.nomorBA, h.jenisBeritaAcara, h.judulPekerjaan, h.generationTimestamp))
            .collect(Collectors.toList());

        return Response.ok(responseList).build();
    }

    @GET
    @Path("/history/{id}/file")
    @Produces("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    @Transactional
    public Response getHistoryFile(@PathParam("id") Long id) {
        // Cari histori berdasarkan ID
        BeritaAcaraHistory history = BeritaAcaraHistory.findById(id);

        if (history == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        // Buat respons dengan konten file .docx
        ResponseBuilder response = Response.ok(new ByteArrayInputStream(history.fileContent));
        response.header("Content-Disposition", "inline; filename=BA-" + history.nomorBA + ".docx");
        return response.build();
    }
    
    private Map<String, String> buildReplacementsMap(BeritaAcaraRequest request) {
        Map<String, String> replacements = new HashMap<>();
        DateToWordsHelper baDate = new DateToWordsHelper(request.tanggalBA);
        DateToWordsHelper pengerjaanDate = new DateToWordsHelper(request.tanggalPengerjaan);
        DateToWordsHelper reqDate = new DateToWordsHelper(request.tanggalSuratRequest);

        Signatory penandatangan1 = request.signatoryList.stream().filter(s -> "penandatangan1".equals(s.tipe)).findFirst().orElse(new Signatory());
        Signatory penandatangan2 = request.signatoryList.stream().filter(s -> "penandatangan2".equals(s.tipe)).findFirst().orElse(new Signatory());
        Signatory penandatangan3 = request.signatoryList.stream().filter(s -> "penandatangan3".equals(s.tipe)).findFirst().orElse(new Signatory());
        Signatory penandatangan4 = request.signatoryList.stream().filter(s -> "penandatangan4".equals(s.tipe)).findFirst().orElse(new Signatory());
        Signatory mengetahui = request.signatoryList.stream().filter(s -> "mengetahui".equals(s.tipe)).findFirst().orElse(new Signatory());
        Fitur fitur = (request.fiturList != null && !request.fiturList.isEmpty()) ? request.fiturList.get(0) : new Fitur();

        replacements.put("${jenisRequest}", "Change Request".equalsIgnoreCase(request.jenisRequest) ? "PERUBAHAN" : "PENGEMBANGAN");
        replacements.put("${namaAplikasiSpesifik}", Objects.toString(request.namaAplikasiSpesifik, ""));
        replacements.put("${nomorBA}", Objects.toString(request.nomorBA, ""));
        replacements.put("${judulPekerjaan}", Objects.toString(request.judulPekerjaan, ""));
        replacements.put("${tahap}", Objects.toString(request.tahap, ""));
        replacements.put("${nomorSuratRequest}", Objects.toString(request.nomorSuratRequest, ""));
        replacements.put("${tanggalSuratRequest}", reqDate.getFormattedDate());
        replacements.put("${nomorBaUat}", Objects.toString(request.nomorBaUat, ""));

        replacements.put("${hariBATerbilang}", baDate.getDayOfWeek());
        replacements.put("${tanggalBATerbilang}", baDate.getDay());
        replacements.put("${bulanBATerbilang}", baDate.getMonth());
        replacements.put("${tahunBATerbilang}", baDate.getYear());
        replacements.put("${tanggalBA}", baDate.getFullDate());
            
        replacements.put("${hariPengerjaanTerbilang}", pengerjaanDate.getDayOfWeek());
        replacements.put("${tanggalPengerjaanTerbilang}", pengerjaanDate.getDay());
        replacements.put("${bulanPengerjaanTerbilang}", pengerjaanDate.getMonth());
        replacements.put("${tahunPengerjaanTerbilang}", pengerjaanDate.getYear());
        replacements.put("${tanggalPengerjaan}", pengerjaanDate.getFullDate());
        
        // Deskripsi fitur tidak diganti di sini, ditangani oleh replacePlaceholderWithHtml
        replacements.put("${fitur.status}", Objects.toString(fitur.status, ""));
        replacements.put("${fitur.keterangan}", Objects.toString(fitur.catatan, ""));

        replacements.put(
            "${signatory.penandatangan1.perusahaan}",
            Objects.toString(penandatangan1.perusahaan, "").replace("<br>", "\n")
        );
        replacements.put("${signatory.penandatangan1.nama}", Objects.toString(penandatangan1.nama, ""));
        replacements.put("${signatory.penandatangan1.jabatan}", Objects.toString(penandatangan1.jabatan, ""));

        replacements.put(
            "${signatory.penandatangan2.perusahaan}",
            Objects.toString(penandatangan2.perusahaan, "").replace("<br>", "\n")
        );
        replacements.put("${signatory.penandatangan2.nama}", Objects.toString(penandatangan2.nama, ""));
        replacements.put("${signatory.penandatangan2.jabatan}", Objects.toString(penandatangan2.jabatan, ""));

        replacements.put(
            "${signatory.penandatangan3.perusahaan}",
            Objects.toString(penandatangan3.perusahaan, "").replace("<br>", "\n")
        );
        replacements.put("${signatory.penandatangan3.nama}", Objects.toString(penandatangan3.nama, ""));
        replacements.put("${signatory.penandatangan3.jabatan}", Objects.toString(penandatangan3.jabatan, ""));

        replacements.put(
            "${signatory.penandatangan4.perusahaan}",
            Objects.toString(penandatangan4.perusahaan, "").replace("<br>", "\n")
        );
        replacements.put("${signatory.penandatangan4.nama}", Objects.toString(penandatangan4.nama, ""));
        replacements.put("${signatory.penandatangan4.jabatan}", Objects.toString(penandatangan4.jabatan, ""));

        replacements.put(
            "${signatory.mengetahui.perusahaan}",
            Objects.toString(mengetahui.perusahaan, "").replace("<br>", "\n")
        );
        replacements.put("${signatory.mengetahui.nama}", Objects.toString(mengetahui.nama, ""));
        replacements.put("${signatory.mengetahui.jabatan}", Objects.toString(mengetahui.jabatan, ""));
        
        
        return replacements;
    }
    

    private void replaceTextPlaceholders(XWPFDocument document, Map<String, String> replacements) {
        for (XWPFParagraph p : document.getParagraphs()) {
            replaceInParagraph(p, replacements);
        }
        for (XWPFTable tbl : document.getTables()) {
            for (XWPFTableRow row : tbl.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph p : cell.getParagraphs()) {
                        // Jangan proses placeholder fitur di sini
                        if (p.getText() != null && p.getText().contains("${fitur.deskripsi}")) {
                            continue;
                        }
                        replaceInParagraph(p, replacements);
                    }
                }
            }
        }
    }

    private void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> replacements) {
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            String placeholder = entry.getKey();
            String replacement = entry.getValue();

            if (!paragraph.getText().contains(placeholder)) {
                continue;
            }

            List<XWPFRun> runs = paragraph.getRuns();
            int startRun = -1, endRun = -1;
            String accumulatedText = "";

            for (int i = 0; i < runs.size(); i++) {
                String runText = runs.get(i).getText(0);
                if (runText == null) continue;

                if (startRun == -1) {
                    if (runText.contains("$")) {
                        startRun = i;
                        accumulatedText = runText;
                    }
                } else {
                    accumulatedText += runText;
                }

                if (startRun != -1) {
                    if (accumulatedText.contains(placeholder)) {
                        endRun = i;
                        
                        XWPFRun styleRun = runs.get(startRun);

                        String newText = accumulatedText.replace(placeholder, replacement);
                        
                        XWPFRun firstRun = runs.get(startRun);
                        
                        if (newText.contains("\n")) {
                            String[] lines = newText.split("\n");
                            firstRun.setText(lines[0], 0);
                            for (int k = 1; k < lines.length; k++) {
                                firstRun.addBreak();
                                firstRun.setText(lines[k]);
                            }
                        } else {
                            firstRun.setText(newText, 0);
                        }

                        firstRun.setBold(styleRun.isBold());
                        firstRun.setItalic(styleRun.isItalic());
                        firstRun.setFontFamily(styleRun.getFontFamily());
                        for (int k = startRun + 1; k <= endRun; k++) {
                            runs.get(k).setText("", 0);
                        }
                        
                        replaceInParagraph(paragraph, replacements);
                        return;
                    } else if (!placeholder.startsWith(accumulatedText)) {
                        startRun = -1;
                        accumulatedText = "";
                    }
                }
            }
        }
    }

    private void replacePlaceholderWithHtml(XWPFDocument document, String placeholder, String html) {
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (int p = 0; p < cell.getParagraphs().size(); p++) {
                        XWPFParagraph paragraph = cell.getParagraphs().get(p);
                        if (paragraph.getText() != null && paragraph.getText().contains(placeholder)) {
                            String fontFamily = null;
                            int fontSize = -1;
                            if (!paragraph.getRuns().isEmpty()) {
                                XWPFRun firstRun = paragraph.getRuns().get(0);
                                fontFamily = firstRun.getFontFamily();
                                fontSize = firstRun.getFontSize();
                            }

                            while (paragraph.getRuns().size() > 0) {
                                paragraph.removeRun(0);
                            }
                            cell.removeParagraph(p);

                            Document htmlDoc = Jsoup.parse(html);
                            XWPFNumbering numbering = document.getNumbering();
                            if(numbering == null) numbering = document.createNumbering();
                            
                            for (Element element : htmlDoc.body().children()) {
                                if (element.tagName().equals("p")) {
                                    XWPFParagraph targetParagraph = cell.addParagraph();
                                    targetParagraph.setSpacingAfter(60);
                                    targetParagraph.setSpacingBefore(240);
                                    targetParagraph.setSpacingBetween(1.0, LineSpacingRule.AUTO);
                                    applyRuns(targetParagraph, element, fontFamily, fontSize);
                                } else if (element.tagName().equals("ul") || element.tagName().equals("ol")) {
                                    BigInteger numId = createNumbering(numbering, element.tagName());
                                    for (Element li : element.select("li")) {
                                        XWPFParagraph listParagraph = cell.addParagraph();
                                        listParagraph.setNumID(numId);

                                        int indentLevel = 0;
                                        if (li.hasClass("ql-indent-1")) indentLevel = 1;
                                        if (li.hasClass("ql-indent-2")) indentLevel = 2;
                                        if (li.hasClass("ql-indent-3")) indentLevel = 3;
                                        
                                        listParagraph.setSpacingAfter(60);
                                        listParagraph.setSpacingBefore(60);
                                        listParagraph.setSpacingBetween(1.0, LineSpacingRule.AUTO);
                                        listParagraph.setNumILvl(BigInteger.valueOf(indentLevel));
                                        applyRuns(listParagraph, li, fontFamily, fontSize);
                                    }
                                }
                            }
                            return; 
                        }
                    }
                }
            }
        }
    }
    
    private void applyRuns(XWPFParagraph paragraph, Element element, String fontFamily, int fontSize) {
        for (Node node : element.childNodes()) {
            XWPFRun run = paragraph.createRun();
            if (fontFamily != null) run.setFontFamily(fontFamily);
            if (fontSize != -1) run.setFontSize(fontSize);

            if (node instanceof TextNode) {
                run.setText(((TextNode) node).text());
            } else if (node instanceof Element) {
                Element childElement = (Element) node;
                run.setText(childElement.text());
                if (childElement.tagName().equals("strong") || childElement.tagName().equals("b")) {
                    run.setBold(true);
                }
                if (childElement.tagName().equals("em") || childElement.tagName().equals("i")) {
                    run.setItalic(true);
                }
                if (childElement.tagName().equals("u")) {
                    run.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
                }
            }
        }
    }
    
    private BigInteger createNumbering(XWPFNumbering numbering, String listType) {
        CTAbstractNum cTAbstractNum = CTAbstractNum.Factory.newInstance();
        cTAbstractNum.setAbstractNumId(BigInteger.valueOf(System.currentTimeMillis() % 100000));

        if ("ul".equals(listType)) {
            addNumberingLevel(cTAbstractNum, 0, STNumberFormat.BULLET, "-");
            addNumberingLevel(cTAbstractNum, 1, STNumberFormat.BULLET, "-");
            addNumberingLevel(cTAbstractNum, 2, STNumberFormat.BULLET, "-");
        } else { // "ol"
            addNumberingLevel(cTAbstractNum, 0, STNumberFormat.DECIMAL, "%1.");
            addNumberingLevel(cTAbstractNum, 1, STNumberFormat.BULLET, "-");
            addNumberingLevel(cTAbstractNum, 2, STNumberFormat.BULLET, "-");
        }

        XWPFAbstractNum abstractNum = new XWPFAbstractNum(cTAbstractNum);
        BigInteger abstractNumId = numbering.addAbstractNum(abstractNum);
        return numbering.addNum(abstractNumId);
    }

    private void addNumberingLevel(CTAbstractNum abstractNum, int level, STNumberFormat.Enum format, String lvlText) {
        CTLvl cTLvl = abstractNum.addNewLvl();
        cTLvl.setIlvl(BigInteger.valueOf(level));
        cTLvl.addNewNumFmt().setVal(format);
        cTLvl.addNewLvlText().setVal(lvlText);
        cTLvl.addNewStart().setVal(BigInteger.valueOf(1));

        long indentLeft, indentRight, indentHanging;
        switch (level) {
            case 0:  
                indentLeft = 512L;
                indentRight = 43L; 
                indentHanging = 360L; 
                break;
            case 1:  
                indentLeft = 945L;
                indentRight = 43L;
                indentHanging = 288L; 
                break;
            case 2:  
                indentLeft = 1200L;
                indentRight = 43L;
                indentHanging = 288L;
                break;
            default: 
                indentLeft = 1200L;
                indentRight = 43L;
                indentHanging = 288L;
                break;
        }
        cTLvl.addNewPPr().addNewInd().setLeft(BigInteger.valueOf(indentLeft));
        cTLvl.addNewPPr().addNewInd().setRight(BigInteger.valueOf(indentRight));
        cTLvl.addNewPPr().addNewInd().setHanging(BigInteger.valueOf(indentHanging));
    }
}