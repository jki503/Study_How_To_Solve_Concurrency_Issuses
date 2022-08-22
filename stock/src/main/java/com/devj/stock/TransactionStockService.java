package com.devj.stock;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import com.devj.stock.service.StockService;

public class TransactionStockService {

	private final EntityManager em;

	private final StockService stockService;

	public TransactionStockService(EntityManager em, StockService stockService) {
		this.em = em;
		this.stockService = stockService;
	}

	public void decrease(Long id, Long quantity){
		EntityTransaction tx = em.getTransaction();
		tx.begin();

		stockService.decrease(id, quantity);

		tx.commit();
	}

}
