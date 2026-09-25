package com.bank.mt940portal.security;

import org.springframework.data.domain.AuditorAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    private final CurrentUser currentUser;

    public AuditorAwareImpl(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    @NonNull
    public Optional<String> currentAuditor() {
        return Optional.ofNullable(currentUser.username());
    }
}
