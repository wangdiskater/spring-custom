package com.spring.custom.dao;

import com.spring.custom.po.User;

import java.util.List;
import java.util.Map;

public interface UserDao {

	List<User> queryUserList(Map<String, Object> param);
}
