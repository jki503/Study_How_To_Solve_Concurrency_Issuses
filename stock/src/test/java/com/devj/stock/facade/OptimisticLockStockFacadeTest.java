package com.devj.stock.facade;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.devj.stock.domain.Stock;
import com.devj.stock.repository.StockRepository;
import com.devj.stock.service.OptimisticLockStockService;
import com.devj.stock.service.PessimisticLockStockService;

@SpringBootTest
class OptimisticLockStockFacadeTest {

	@Autowired
	private OptimisticLockStockFacade stockService;

	@Autowired
	private StockRepository stockRepository;

	@BeforeEach
	void setup(){
		Stock stock = new Stock(1L, 100L);
		stockRepository.saveAndFlush(stock);
	}

	@AfterEach
	void teardown(){
		stockRepository.deleteAll();
	}

	@Test
	public void 동시에_100개의_요청() throws InterruptedException {
		int threadCount = 100;
		ExecutorService executorService = Executors.newFixedThreadPool(32);
		CountDownLatch latch = new CountDownLatch(threadCount); // 다른 스레드에서 수행중인 작업이 완료될때까지 대기할 수 있도록 도와주는 클래스

		for (int i = 0 ; i < threadCount; i++){
			executorService.submit(
				() -> {
					try {
						stockService.decrease(1L, 1L);
					} catch (InterruptedException e) {
						e.printStackTrace();
					} finally {
						latch.countDown();
					}
				});
		}
		latch.await();

		Stock stock = stockRepository.findById(1L).orElseThrow();
		assertThat(stock.getQuantity()).isEqualTo(0L);
	}
}