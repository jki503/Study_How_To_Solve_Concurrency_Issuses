---
Title: 재고시스템으로 알아보는 동시성 이슈 해결방법
Category: Spring, Java, Concurrency
Author: Jung
---

- [프로젝트 환경](#프로젝트-환경)
- [재고시스템 만들어보기](#재고시스템-만들어보기)
	- [현재와 같은 상황에서 동시성 문제점](#현재와-같은-상황에서-동시성-문제점)
	- [Race Condition](#race-condition)
- [Synchronized 사용하기](#synchronized-사용하기)
- [Database 사용하기](#database-사용하기)
- [Redis 사용하기](#redis-사용하기)
- [마무리 - MySQL과 Redis 비교](#마무리---mysql과-redis-비교)

## 프로젝트 환경

## 재고시스템 만들어보기

</br>

> 간단한 재고 entity를 만든 후 내부 재고를 감소시키는 내부 비즈니스 로직을 담는 decrease 메서드를 작성한다.  
> 이후 일반적으로 테스트를 작성하면 별 문제 없이 통과되는 경우를 확인할 수 있다.

</br>

```java
@Entity
public class Stock {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long productId;

	private Long quantity;

	protected Stock(){}

	public Stock(Long productId, Long quantity) {
		this.productId = productId;
		this.quantity = quantity;
	}

	public Long getQuantity() {
		return quantity;
	}

	public void decrease(Long quantity){
		Assert.isTrue(this.quantity >= quantity, "재고가 부족합니다.");
		this.quantity -= quantity;
	}
}
```

```java
@Service
@Transactional
public class StockService {

	private final StockRepository stockRepository;

	public StockService(StockRepository stockRepository) {
		this.stockRepository = stockRepository;
	}

	public void decrease(Long id, Long quantity){
		//get stock
		Stock stock = stockRepository.findById(id).orElseThrow();

		//재고 감소
		stock.decrease(quantity);
	}
}

```

```java
@SpringBootTest
class StockServiceTest {

	@Autowired
	private StockService stockService;

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
	public void 재고감소_확인(){
		stockService.decrease(1L, 99L);

		// 100 - 1 = 99

		Stock stock = stockRepository.findById(1L).orElseThrow();

		assertThat(stock.getQuantity()).isEqualTo(99L);
	}
}
```

|                테스트 결과 확인                 |
| :---------------------------------------------: |
| ![테스트 결과 확인](./res/_02_test_success.png) |

</br>

### 현재와 같은 상황에서 동시성 문제점

</br>

> 만약 해당 api의 요청이 동시에 몰리게 될 경우,  
> 해당 사용자에게 정확한 재고 수량을 보장해줄 수 있는가?

</br>

- 100개의 스레드로 동시에 id가 1인 stock의 재고를 1씩 감소시켰다.
- 예상과 다르게 남의 재고의 개수가 0과 같지 않을 경우 테스트가 성공해야한다.

```java
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
					}
					finally {
						latch.countDown();
					}
				});
		}
		latch.await();

		Stock stock = stockRepository.findById(1L).orElseThrow();
		assertThat(stock.getQuantity()).isNotEqualTo(0L);
	}
```

</br>

|                                          재고의 개수가 0이 아닌 테스트 성공                                           |
| :-------------------------------------------------------------------------------------------------------------------: |
| ![재고의 개수가 0이 아닌 테스트 성공](../res/../Study_How_To_Solve_Concurrency_Issuses/res/_02_stock_concurrency.png) |

</br>

- 우리가 기존에 작성했던 방식은 단일 스레드에서만 값이 보장하는 상황이다.
- 현재 멀티스레드를 돌려 동시에 요청이 들어갈 경우 동시성 이슈가 발생한다.
  - `race condition 발생`

</br>

### Race Condition

</br>

|                   Thread - 1                    |        Stock        |                   Thread - 2                    |
| :---------------------------------------------: | :-----------------: | :---------------------------------------------: |
|        select \* from stock where id = 1        | {id: 1, quantity:5} |                                                 |
|                                                 | {id: 1, quantity:5} |        select \* from stock where id = 1        |
| update set quantity = 4 from stock where id = 1 | {id: 1, quantity:4} |                                                 |
|                                                 | {id: 1, quantity:4} | update set quantity = 4 from stock where id = 1 |

</br>

- 수업 내용

> 예를 들어 Thread1에서 stock을 조회하고 업데이트하기 직전에, thread2가 조회를 할 경우  
> 서로 같은 데이터를 조회한 후 같은 quantity를 4로 업데이트하기 때문에 race condition이 발생했다고 설명한다.

</br>

- 보충

</br>

- select query
- 재고 감소
- update query

> 이렇게 3개의 명령어가 실행된다.  
> 쿼리가 여러개 날아가고, 우리가 실행하는 명령어의 개수가 여러개여서  
> 중간에 다른 스레드가 끼어들어서 발생하는 것이다라고 생각하면 안된다.
> 정확히 어떤 상황에서 레이스 컨디션이 발생되는지 조금더 자세히 알아보기 위해 내용을 보충하겠다.

</br>

- Race Condition

> 공유하는 데이터에 대하여 여러개의 프로세스 및 스레드가 동시에 접근하여 값을 변경할 때  
> 특정한 접근 순서에 따라 결과가 달라지는 것을 의미한다.
>
> 현재 재고는 특정 사용자에 대하여 접근이 국한되지 않고, 멀티스레드에서 요청이 발생 될 수 있다.  
> 현재 Stock에 대한 접근에서 동시화 하지 않고 테이블에 접근함으로 race condition이 발생한다.

</br>

## Synchronized 사용하기

> 따라서 하나의 스레드씩 공유 데이터에 접근하도록 해주면 된다.

```java
@Service
@Transactional
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
	}
}

```

```java

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
					}
					finally {
						latch.countDown();
					}
				});
		}
		latch.await();

		Stock stock = stockRepository.findById(1L).orElseThrow();
		assertThat(stock.getQuantity()).isEqualTo(0L);
	}
```

|                Test 실패                 |
| :--------------------------------------: |
| ![Test 결과](./res/_03_synchronized.png) |

</br>

- synchronized를 사용했음에도 test가 실패하는 이유

  - @Transactional을 사용하면 우리가 만든 클래스를 래핑한 클래스를 새로 만들어서 실행
  - 간략하게 설명하면 StockService를 인스턴스 변수로 갖는 새로운 클래스가 생성된다.

- 대략적인 예시

```java
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
```

> 현재 동시성 이슈에서는 synchronized 키워드를 사용했다 하더라도 트랜잭셔인 반영되기전에  
> 다른 스레드가 데이터에 접근할 경우 레이스 컨디션이 발생한다.

</br>

- 이 경우 @Transactional 어노테이션을 제거
- Jpa dirty checking 지원 말고, 직접 영속화 하기로 해결 가능하다

```java
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

    // update
    stockRepository.saveAndFlush(stock);
	}
}

```

|                    Test 성공                     |
| :----------------------------------------------: |
| ![Test 성공](./res/_03_synchronized_success.png) |

</br>

## Database 사용하기

## Redis 사용하기

## 마무리 - MySQL과 Redis 비교
