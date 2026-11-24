package com.study.cockpit.service;

import com.study.cockpit.common.BusinessException;
import com.study.cockpit.common.ResultCode;
import com.study.cockpit.config.AppProperties;
import com.study.cockpit.dto.AuthDTO;
import com.study.cockpit.entity.SysUser;
import com.study.cockpit.mapper.SysUserMapper;
import com.study.cockpit.security.JwtTokenProvider;
import com.study.cockpit.security.SecurityCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthRegistrationTest {
    private SysUserMapper users;
    private PasswordEncoder passwords;
    private AppProperties props;
    private AuthService service;
    private AuthDTO.RegisterReq request;

    @BeforeEach
    void setup() {
        users = mock(SysUserMapper.class);
        passwords = mock(PasswordEncoder.class);
        props = mock(AppProperties.class);
        when(props.isAllowRegister()).thenReturn(true);
        when(passwords.encode("test-password")).thenReturn("bcrypt-hash");
        service = new AuthService(users, passwords, mock(JwtTokenProvider.class),
                mock(SecurityCacheService.class), props);
        request = new AuthDTO.RegisterReq();
        request.setUsername("test_user");
        request.setPassword("test-password");
    }

    @Test
    void storesEncodedPasswordAndInitialSessionVersion() {
        service.register(request);
        ArgumentCaptor<SysUser> saved = ArgumentCaptor.forClass(SysUser.class);
        verify(users).insert(saved.capture());
        assertEquals("bcrypt-hash", saved.getValue().getPassword());
        assertEquals(1, saved.getValue().getStatus());
        assertEquals(0, saved.getValue().getTokenVersion());
    }

    @Test
    void disabledRegistrationNeverTouchesDatabase() {
        when(props.isAllowRegister()).thenReturn(false);
        BusinessException error = assertThrows(BusinessException.class, () -> service.register(request));
        assertEquals(ResultCode.FORBIDDEN, error.getResultCode());
        verifyNoInteractions(users, passwords);
    }

    @Test
    void duplicateUsernameIsAUserFacingError() {
        when(users.selectByUsername("test_user")).thenReturn(new SysUser());
        BusinessException error = assertThrows(BusinessException.class, () -> service.register(request));
        assertEquals("用户名已存在", error.getMessage());
        verify(users, never()).insert(any(SysUser.class));
    }

    @Test
    void concurrentDuplicateIsAlsoAUserFacingError() {
        when(users.insert(any(SysUser.class))).thenThrow(new DuplicateKeyException("unique username"));
        BusinessException error = assertThrows(BusinessException.class, () -> service.register(request));
        assertEquals("用户名已存在", error.getMessage());
        assertEquals(ResultCode.PARAM_ERROR, error.getResultCode());
    }

    @Test
    void rejectsMultibytePasswordBeyondBcryptLimit() {
        request.setPassword("中".repeat(25));
        assertThrows(BusinessException.class, () -> service.register(request));
        verifyNoInteractions(users, passwords);
    }

    @Test
    void acceptsPasswordAtBcryptByteLimit() {
        request.setPassword("中".repeat(24));
        assertDoesNotThrow(() -> service.register(request));
        verify(passwords).encode(request.getPassword());
    }
}
