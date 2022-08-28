package com.devj.stock.facade;

import org.springframework.stereotype.Service;

import com.devj.stock.service.OptimisticLockStockService;

@Service
public class OptimisticLockStockFacade {

	private OptimisticLockStockService optimisticLockStockService;

	public OptimisticLockStockFacade(OptimisticLockStockService optimisticLockStockService) {
		this.optimisticLockStockService = optimisticLockStockService;
	}

	public void decrease(Long id, Long quantity) throws InterruptedException {
		while (true){
			try {
				optimisticLockStockService.decrease(id, quantity);
				break;
			}catch (Exception e){
				Thread.sleep(50);
			}
		}
	}
}
