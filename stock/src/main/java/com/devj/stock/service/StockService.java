package com.devj.stock.service;

import org.hibernate.annotations.Synchronize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.devj.stock.domain.Stock;
import com.devj.stock.repository.StockRepository;

@Service
// @Transactional
public class StockService {

	private final StockRepository stockRepository;

	public StockService(StockRepository stockRepository) {
		this.stockRepository = stockRepository;
	}

	public synchronized void decrease(Long id, Long quantity){
		//get stock
		Stock stock = stockRepository.findById(id).orElseThrow();

		//재고 감소
		stock.decrease(quantity);

		stockRepository.saveAndFlush(stock);
	}
}
