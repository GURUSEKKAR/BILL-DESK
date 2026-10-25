package com.invoice;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.invoice.modules.jobs.service.JobService;
import com.invoice.modules.reminders.repository.ReminderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 deliverable: a reminder email is sent once and includes the counter
 * name in the message body.
 */
class ReminderCounterNameTest extends Phase3BaseTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    @Autowired JobService jobService;
    @Autowired ReminderRepository reminderRepository;
    @Autowired org.springframework.context.ApplicationContext ctx;

    @Test
    void reminderEmailContainsCounterName() throws Exception {
        var owner = registerBusiness("Shop", "owner@shop.test").accessToken();
        UUID counter = createCounter(owner, "Front Desk", "C2");
        // Customer with email so the reminder actually sends.
        var cust = postAuth("/customers", owner,
            Map.of("name", "Carol", "email", "carol@test.com"),
            com.invoice.modules.customers.dto.CustomerResponse.class).getBody();

        var inv = createInvoice(owner, cust.id(), counter, 50000, LocalDate.now().plusDays(30));
        sendInvoiceResp(owner, inv.id());

        // Drain PDF/invoice-email jobs; focus on a single reminder.
        jdbcTemplate.update("DELETE FROM jobs WHERE type = 'send_invoice_email'");
        var reminder = reminderRepository.findByInvoiceId(inv.id()).get(0);
        jobService.enqueue("send_reminder", Map.of("reminderId", reminder.getId().toString()));
        jdbcTemplate.update("UPDATE jobs SET next_run_at = now() - interval '1 second'");

        // Process the single reminder job.
        var claimed = jobService.claimNextJob().orElseThrow();
        var handler = ctx.getBean(com.invoice.modules.worker.JobHandlerRegistry.class)
            .get(claimed.getType());
        var payload = ctx.getBean(tools.jackson.databind.ObjectMapper.class)
            .readValue(claimed.getPayload(),
                new tools.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
        handler.handle(payload);
        jobService.complete(claimed);

        var messages = greenMail.getReceivedMessages();
        assertThat(messages).hasSize(1);
        String content = com.icegreen.greenmail.util.GreenMailUtil.getBody(messages[0]);
        // Counter name appears in the reminder body.
        assertThat(content).contains("Front Desk");
    }
}
