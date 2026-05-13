package com.popclub.api.util;

import com.popclub.api.dto.LoginResponseDto;
import com.popclub.api.dto.SignupResponseDto;
import com.popclub.api.impl.AuthException;
import com.popclub.api.impl.WebSetupApiImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebOnboardingSetup {

    private static final Logger log = LoggerFactory.getLogger(WebOnboardingSetup.class);
    private final WebSetupApiImpl api = new WebSetupApiImpl();

    /**
     * Finds a first-time-user phone number and returns it.
     * Does NOT call signup or markHybridJourney — call setupAfterWebLogin() after the web OTP flow completes.
     */
    public String prepare() {
        log.info("[WebOnboardingSetup] Preparing phone for onboarding");

        String resumed = PhoneNumberManager.nextOnboardingStarted();
        if (resumed != null) { log.info("[WebOnboardingSetup] Resuming ONBOARDING_STARTED with {}", resumed); return resumed; }

        String pending = PhoneNumberManager.nextFirstTimeFound();
        if (pending != null) { log.info("[WebOnboardingSetup] Resuming FIRST_TIME_FOUND with {} — OTP was not completed last run", pending); return pending; }

        while (true) {
            String phone = PhoneNumberManager.nextUntried();
            if (phone == null) throw new RuntimeException("No untried phone numbers left in range");

            log.info("[WebOnboardingSetup] Trying {}", phone);

            LoginResponseDto loginResp;
            try { loginResp = api.login(phone); }
            catch (AuthException e) {
                throw new RuntimeException("[WebOnboardingSetup] Auth failure — check APP_TOKEN in local.properties", e);
            }
            catch (Exception e) {
                log.warn("[WebOnboardingSetup] login failed for {} — skipping", phone, e);
                PhoneNumberManager.markNotFirstTime(phone); continue;
            }

            if (!loginResp.isFirstTimeUser()) {
                log.info("[WebOnboardingSetup] {} not first_time_user — skipping", phone);
                PhoneNumberManager.markNotFirstTime(phone); continue;
            }

            PhoneNumberManager.markFirstTimeFound(phone);
            log.info("[WebOnboardingSetup] {} is first_time_user — saved as FIRST_TIME_FOUND, ready for web onboarding", phone);
            return phone;
        }
    }

    /**
     * Called after the web OTP login step completes.
     * Calls signup to get the userId, then marks the hybrid journey.
     */
    public void setupAfterWebLogin(String phone) {
        log.info("[WebOnboardingSetup] Post-web-login setup for {}", phone);

        SignupResponseDto signupResp = api.signup(phone);
        String userId = signupResp.resolvedUserId();
        if (userId == null || userId.isBlank())
            throw new RuntimeException("signup returned no userId for " + phone);

        log.info("[WebOnboardingSetup] signup userId={} — marking hybrid journey", userId);
        api.markHybridJourney(userId);

        PhoneNumberManager.markOnboardingStarted(phone);
        log.info("[WebOnboardingSetup] {} fully prepared — userId={}", phone, userId);
    }

    public static void markEkycDone(String phone) {
        PhoneNumberManager.markEkycCompleted(phone);
    }
}
