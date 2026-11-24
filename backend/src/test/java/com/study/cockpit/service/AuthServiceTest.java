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
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private SysUserMapper users;
    private PasswordEncoder passwords;
    private AuthService service;
    private AuthDTO.ChangePasswordReq request;

    @BeforeEach
    void setup() {
        users = mock(SysUserMapper.class);
        passwords = mock(PasswordEncoder.class);
        service = new AuthService(users, passwords, mock(JwtTokenProvider.class),
                mock(SecurityCacheService.class), mock(AppProperties.class));
        SysUser user = new SysUser();
        user.setId(1L);
        user.setStatus(1);
        user.setPassword("stored-hash");
        user.setTokenVersion(3);
        when(users.selectById(1L)).thenReturn(user);
        when(passwords.matches("old-password", "stored-hash")).thenReturn(true);
        when(passwords.encode("new-password")).thenReturn("new-hash");
        request = new AuthDTO.ChangePasswordReq();
        request.setOldPassword("old-password");
        request.setNewPassword("new-password");
    }

    @Test
    void writesPasswordAndSessionVersionAtomically() {
        when(users.changePassword(1L, "new-hash", 3)).thenReturn(1);
        assertDoesNotThrow(() -> service.changePassword(1L, request));
        verify(users).changePassword(1L, "new-hash", 3);
        verify(users, never()).incrementTokenVersion(anyLong());
    }

    @Test
    void rejectsWrongCurrentPasswordWithoutWriting() {
        request.setOldPassword("wrong-password");
        assertThrows(BusinessException.class, () -> service.changePassword(1L, request));
        verify(users, never()).changePassword(anyLong(), anyString(), anyInt());
    }

    @Test
    void rejectsSamePasswordWithoutWriting() {
        request.setNewPassword("old-password");
        assertThrows(BusinessException.class, () -> service.changePassword(1L, request));
        verify(users, never()).changePassword(anyLong(), anyString(), anyInt());
    }

    @Test
    void rejectsConcurrentChangeInsteadOfOverwritingIt() {
        when(users.changePassword(1L, "new-hash", 3)).thenReturn(0);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.changePassword(1L, request));
        assertEquals(ResultCode.UNAUTHORIZED, error.getResultCode());
    }

    @Test
    void rejectsMissingUserWithoutWriting() {
        when(users.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.changePassword(1L, request));
        verify(users, never()).changePassword(anyLong(), anyString(), anyInt());
    }
}
