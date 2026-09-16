package com.extremis.hub.payment.phonepe;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Backed by extremis.payment.phonepe.* (env vars PHONEPE_*). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "extremis.payment.phonepe")
public class PhonePeProperties {

    /** Left blank until the founder completes onboarding -- see PaymentProviderConfig. */
    private String clientId;
    private String clientSecret;

    /**
     * PhonePe's own client versioning identifier (a plain integer as a
     * string, e.g. "1"), supplied alongside clientId/clientSecret when
     * PhonePe provisions API access -- not a version of our own code.
     */
    private String clientVersion;

    /** true = api-preprod.phonepe.com sandbox, false = api.phonepe.com production -- flip only when ready to accept real money. */
    private boolean sandbox = true;

    /**
     * Webhook Basic-Auth-style credentials the founder configures in
     * the PhonePe Business Dashboard when setting up the webhook URL --
     * unlike Cashfree, PhonePe's webhook destination URL itself is
     * dashboard-configured, not sent in our API requests, so there's
     * no notify-url property here. PhonePe echoes
     * Authorization: SHA256(username:password) back on every webhook
     * call so we can verify it came from them.
     */
    private String webhookUsername;
    private String webhookPassword;
}
