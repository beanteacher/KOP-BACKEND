package com.kop.finance.dto;

import com.kop.finance.domain.Client;
import jakarta.validation.constraints.*;

import java.time.Instant;

/** 06-api-design.md Finance API — 거래처 `/api/clients`. */
public class ClientDto {

    public record RegisterRequest(
        @NotBlank(message = "사업자번호는 필수입니다") @Pattern(regexp = "\\d{10}", message = "사업자번호는 하이픈 없이 10자리 숫자로 입력하세요")
        String businessRegistrationNumber,
        @NotBlank(message = "상호는 필수입니다") @Size(max = 100, message = "상호는 100자를 넘을 수 없습니다") String name,
        @NotBlank(message = "대표자명은 필수입니다") @Size(max = 50, message = "대표자명은 50자를 넘을 수 없습니다") String representativeName,
        @NotBlank(message = "업태는 필수입니다") @Size(max = 100, message = "업태는 100자를 넘을 수 없습니다") String businessType,
        @NotBlank(message = "종목은 필수입니다") @Size(max = 100, message = "종목은 100자를 넘을 수 없습니다") String businessItem,
        @NotBlank(message = "사업장 주소는 필수입니다") @Size(max = 255, message = "주소는 255자를 넘을 수 없습니다") String address,
        @Email(message = "올바른 이메일 형식이 아닙니다") @Size(max = 255) String email,
        @Size(max = 50, message = "담당자명은 50자를 넘을 수 없습니다") String contactName,
        @Size(max = 20, message = "담당자 연락처는 20자를 넘을 수 없습니다") String contactPhone,
        @NotBlank(message = "과세유형은 필수입니다") String taxType
    ) {}

    /** status도 여기 포함 — B-1 "삭제 불가 시 비활성화 권장"이 이 PATCH로 이뤄진다(ACTIVE로 재전환도 동일). */
    public record UpdateRequest(
        @NotBlank(message = "사업자번호는 필수입니다") @Pattern(regexp = "\\d{10}", message = "사업자번호는 하이픈 없이 10자리 숫자로 입력하세요")
        String businessRegistrationNumber,
        @NotBlank(message = "상호는 필수입니다") @Size(max = 100, message = "상호는 100자를 넘을 수 없습니다") String name,
        @NotBlank(message = "대표자명은 필수입니다") @Size(max = 50, message = "대표자명은 50자를 넘을 수 없습니다") String representativeName,
        @NotBlank(message = "업태는 필수입니다") @Size(max = 100, message = "업태는 100자를 넘을 수 없습니다") String businessType,
        @NotBlank(message = "종목은 필수입니다") @Size(max = 100, message = "종목은 100자를 넘을 수 없습니다") String businessItem,
        @NotBlank(message = "사업장 주소는 필수입니다") @Size(max = 255, message = "주소는 255자를 넘을 수 없습니다") String address,
        @Email(message = "올바른 이메일 형식이 아닙니다") @Size(max = 255) String email,
        @Size(max = 50, message = "담당자명은 50자를 넘을 수 없습니다") String contactName,
        @Size(max = 20, message = "담당자 연락처는 20자를 넘을 수 없습니다") String contactPhone,
        @NotBlank(message = "상태는 필수입니다") String status,
        @NotBlank(message = "과세유형은 필수입니다") String taxType
    ) {}

    public record Response(
        String id, String businessRegistrationNumber, String name, String representativeName,
        String businessType, String businessItem, String address, String email,
        String contactName, String contactPhone, String status, String taxType, Instant createdAt
    ) {
        public static Response from(Client c) {
            return new Response(
                c.getId().toString(), c.getBusinessRegistrationNumber(), c.getName(), c.getRepresentativeName(),
                c.getBusinessType(), c.getBusinessItem(), c.getAddress(), c.getEmail(),
                c.getContactName(), c.getContactPhone(), c.getStatus().name(), c.getTaxType().name(), c.getCreatedAt()
            );
        }
    }

    public record ListItem(
        String id, String businessRegistrationNumber, String name, String representativeName, String status, String taxType
    ) {
        public static ListItem from(Client c) {
            return new ListItem(
                c.getId().toString(), c.getBusinessRegistrationNumber(), c.getName(), c.getRepresentativeName(),
                c.getStatus().name(), c.getTaxType().name()
            );
        }
    }
}
