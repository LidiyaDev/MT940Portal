package com.bank.mt940portal.email;

import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One outbound statement notification. */
@Getter
@Builder
public class EmailMessage {

    private String fromAddress;
    private String fromName;
    private List<String> to;
    private List<String> cc;
    private List<String> bcc;
    private String subject;
    private String body;

    private String attachmentFileName;
    private String attachmentContentType;
    private byte[] attachment;

    /** Extra values available to the payload template. */
    @Builder.Default
    private Map<String, String> variables = new HashMap<>();

    private String correlationId;
}
