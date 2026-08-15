package com.cassierq.api.report;

import com.cassierq.api.report.dto.SalesSummaryResponse;
import com.cassierq.api.security.AppUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reports", description = "Ringkasan penjualan toko user yang sedang login")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/summary")
    @Operation(summary = "Ringkasan penjualan: gross sales, jumlah transaksi, top 5 produk terlaris (default 7 hari terakhir)")
    public SalesSummaryResponse summary(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.summary(principal, from, to);
    }
}
