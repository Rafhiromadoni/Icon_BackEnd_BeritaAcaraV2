package com.rafhi.dto;

import java.util.List;

public class BeritaAcaraRequest {
    
    public String jenisBeritaAcara; // "UAT" atau "Deployment"
    public String jenisRequest; // "Change Request" atau "Job Request"
    public String namaAplikasiSpesifik;
    public String judulPekerjaan;
    public String tahap; // "tahap I", "tahap II", dll.

    public String nomorBA;
    public String nomorSuratRequest;
    public String nomorBaUat; // Khusus untuk Deployment

    public String tanggalBA;
    public String tanggalSuratRequest;
    public String tanggalPengerjaan;
    
    public List<Fitur> fiturList;
    public List<Signatory> signatoryList;
    
}