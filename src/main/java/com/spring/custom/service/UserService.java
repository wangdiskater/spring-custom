package com.spring.custom.service;

import com.spring.custom.po.User;

import java.util.List;
import java.util.Map;

public interface UserService {
	List<User> queryUsers(Map<String, Object> param);
}
