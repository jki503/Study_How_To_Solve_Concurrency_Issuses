package com.devj.stock.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.devj.stock.domain.Stock;

public interface StockRepository extends JpaRepository<Stock, Long> {
	
	Stock findByIdWithPessimisticLock(Long id);
}
