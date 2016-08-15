package com.rslima.ricash.web.account;

import com.rslima.ricash.domain.account.Account;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

/** 
* AccountController Tester. 
* 
* @author <Authors name> 
* @since <pre>Aug 15, 2016</pre> 
* @version 1.0 
*/ 
public class AccountControllerTest { 

@Before
public void before() throws Exception { 
} 

@After
public void after() throws Exception { 
} 

/** 
* 
* Method: getAll() 
* 
*/ 
@Test
public void testGetAll() throws Exception {

    AccountController accountController = new AccountController();
    UUID userId = UUID.randomUUID();

    List<Account> expected = Collections.emptyList();

    assertEquals("Stub Failed!", expected, accountController.getAll(userId));

} 


} 
