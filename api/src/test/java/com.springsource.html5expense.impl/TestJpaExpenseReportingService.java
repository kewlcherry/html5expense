/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.springsource.html5expense.impl;


import com.springsource.html5expense.EligibleCharge;
import com.springsource.html5expense.Expense;
import com.springsource.html5expense.ExpenseReportingService;
import com.springsource.html5expense.State;
import com.springsource.html5expense.config.ComponentConfig;
import junit.framework.Assert;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = ComponentConfig.class)
public class TestJpaExpenseReportingService {
	@Inject
	private ExpenseReportingService expenseReportingService;

	@Inject private PlatformTransactionManager transactionManager;

	@PersistenceContext private EntityManager entityManager;

	private TransactionTemplate transactionTemplate;

	private String itsMission = "\"to go... where no man... has gone before!\"";
	private EligibleChargeEntity inExpensiveCharge, expensiveCharge;
	private List<Long> charges = new ArrayList<Long>();

	@Before
	public void installSomeCharges() throws Throwable {

		// kind of cheating since i dont want to expose a separate Charge service, so we'll force-feed the database some sample data
		transactionTemplate = new TransactionTemplate(this.transactionManager);
		this.charges = transactionTemplate.execute(new TransactionCallback<List<Long>>() {
			public List<Long> doInTransaction(TransactionStatus status) {
				inExpensiveCharge = new EligibleChargeEntity(new LocalDate(), "Starbucks", "food", new BigDecimal(4));
				entityManager.persist(inExpensiveCharge);

				expensiveCharge = new EligibleChargeEntity(new LocalDate(), "dinner at Morton's Steak House", "food", new BigDecimal(59.99));
				entityManager.persist(expensiveCharge);
				return Arrays.asList(inExpensiveCharge.getId(), expensiveCharge.getId());
			}
		});
	}

	@Test
	public void testIdentifyingEligibleCharges() throws Throwable {
		Collection<EligibleCharge> eligibleCharges = expenseReportingService.getEligibleCharges();
		Assert.assertTrue(eligibleCharges.size() == this.charges.size());
	}

	@Test
	public void testCreateReport() throws Throwable {
		Long expenseReportId = expenseReportingService.createReport(itsMission);
		Assert.assertTrue("the ID must be greater than 0", expenseReportId > 0);
		Assert.assertEquals(1, expenseReportingService.getOpenReports().size());
		Assert.assertEquals(entityManager.find(ExpenseReportEntity.class, expenseReportId).getPurpose(), itsMission);
	}

	@Test
	public void testCreateExpenses() throws Throwable {
		Long expenseReportId = expenseReportingService.createReport(itsMission);
		Collection<Expense> expenseCollection = expenseReportingService.createExpenses(expenseReportId, this.charges);
		Assert.assertNotNull(expenseCollection);
		Assert.assertTrue(expenseCollection.size() == 2);
	}

	@Test
	public void testAttachingReceipts () throws  Throwable {
		Long expenseReportId = expenseReportingService.createReport( itsMission);
		Collection<Expense> expenses = expenseReportingService.createExpenses(expenseReportId, Arrays.asList(expensiveCharge.getId()));
		Integer  expenseId  = expenses.iterator().next().getId();
		String receiptClaim = expenseReportingService.attachReceipt(expenseReportId,  expenseId , new byte[0] ) ;
		Assert.assertNotNull(receiptClaim);
		ExpenseEntity entity = entityManager.find( ExpenseEntity.class, expenseId);
		Assert.assertFalse(entity.isFlagged());
		Assert.assertEquals( receiptClaim , entity.getReceipt() );
	}

	@Test
	@Transactional
	public void testSubmittingReports() throws Throwable {
		Long expenseReportId = expenseReportingService.createReport(itsMission);
		expenseReportingService.submitReport(expenseReportId);
		ExpenseReportEntity erEntity = entityManager.find(ExpenseReportEntity.class, expenseReportId);
		Assert.assertEquals(erEntity.data().getState(), State.IN_REVIEW);
	}

}