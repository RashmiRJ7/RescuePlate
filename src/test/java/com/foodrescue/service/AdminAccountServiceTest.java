package com.foodrescue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.foodrescue.dto.InitialAdminSetupRequest;
import com.foodrescue.entity.User;
import com.foodrescue.enums.Role;
import com.foodrescue.exception.DuplicateResourceException;
import com.foodrescue.exception.ValidationException;
import com.foodrescue.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PlatformTransactionManager transactionManager;

    private AdminAccountService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        service = new AdminAccountService(userRepository, passwordEncoder, transactionManager, "out-of-band-key");
    }

    @Test
    void createsFirstAdminWithServerAssignedRoleAndEncodedPassword() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("LongAndSecurePassword1!")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        var response = service.createInitialAdmin(request("out-of-band-key", "LongAndSecurePassword1!",
                "LongAndSecurePassword1!"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void rejectsIncorrectSetupKeyWithoutSavingAnAccount() {
        assertThatThrownBy(() -> service.createInitialAdmin(
                request("wrong-key", "LongAndSecurePassword1!", "LongAndSecurePassword1!")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsSetupWhenAnAdminAlreadyExists() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> service.createInitialAdmin(
                request("out-of-band-key", "LongAndSecurePassword1!", "LongAndSecurePassword1!")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void rejectsMismatchedConfirmation() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> service.createInitialAdmin(
                request("out-of-band-key", "LongAndSecurePassword1!", "DifferentPassword2!")))
                .isInstanceOf(ValidationException.class)
                .satisfies(error -> assertThat(((ValidationException) error).getFieldErrors())
                        .containsKey("confirmPassword"));
    }

    private InitialAdminSetupRequest request(String key, String password, String confirmation) {
        return new InitialAdminSetupRequest(
                "RescuePlate Admin",
                "admin@example.com",
                password,
                confirmation,
                "9876543210",
                key);
    }
}
