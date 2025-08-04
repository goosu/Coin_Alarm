// src/main/java/coinalarm/Coin_Alarm/AccessingDataJpaApplication.java
package coinalarm.Coin_Alarm; // <-- 실제 프로젝트 기본 패키지 이름

import coinalarm.Coin_Alarm.coin.CoinService;
// 필요한 스프링 부트 및 기타 라이브러리 임포트
//import coin.CoinService; // <-- CoinService 클래스 임포트 (실제 패키지 경로 사용)
import org.slf4j.Logger; // 로깅을 위해 필요합니다.
import org.slf4j.LoggerFactory; // 로거 객체를 생성하는 팩토리입니다.
import org.springframework.boot.CommandLineRunner; // 애플리케이션 시작 시 특정 코드를 실행하기 위한 인터페이스입니다.
import org.springframework.boot.SpringApplication; // 스프링 부트 애플리케이션을 실행하는 클래스입니다.
import org.springframework.boot.autoconfigure.SpringBootApplication; // 스프링 부트 애플리케이션의 핵심 어노테이션입니다.
import org.springframework.context.annotation.Bean; // Spring 컨테이너에 Bean을 등록할 때 사용합니다.


import org.springframework.scheduling.annotation.EnableScheduling; // <-- @EnableScheduling 임포트

@SpringBootApplication // @SpringBootApplication: 이 어노테이션 하나로 스프링 부트 애플리케이션의 핵심 설정이 완료됩니다.
@EnableScheduling // <-- 스케줄링 활성화 어노테이션 추가
// @EnableAutoConfiguration, @ComponentScan, @Configuration 세 가지 어노테이션을 포함하고 있습니다.
// - @EnableAutoConfiguration: 클래스패스에 있는 라이브러리들을 기반으로 애플리케이션 설정을 자동으로 구성합니다. (예: spring-boot-starter-web이 있으면 웹 서버 설정 자동 구성)
// - @ComponentScan: 현재 패키지 및 하위 패키지에서 @Component, @Service, @Repository, @Controller 등의 어노테이션이 붙은 클래스들을 찾아 Spring Bean으로 등록합니다.
// - @Configuration: 이 클래스를 설정 클래스로 사용함을 나타냅니다.
public class AccessingDataJpaApplication { // 클래스 이름은 프로젝트에 맞게 변경될 수 있습니다.

	// 로깅을 위한 Logger 객체를 생성합니다. 애플리케이션 실행 중 정보를 출력할 때 사용합니다.
	private static final Logger log = LoggerFactory.getLogger(AccessingDataJpaApplication.class);

	// 애플리케이션의 진입점(Entry Point)인 main 메서드입니다.
	public static void main(String[] args) {
		// SpringApplication.run(): 스프링 부트 애플리케이션을 실행합니다.
		// 첫 번째 인자는 메인 애플리케이션 클래스이고, 두 번째 인자는 커맨드 라인 인자입니다.
		SpringApplication.run(AccessingDataJpaApplication.class, args);
		// 애플리케이션 시작 완료 로그를 출력합니다.
		log.info("Coin Alarm Backend Application Started!");
	}

	/**
	 * 애플리케이션이 완전히 시작된 후 특정 코드를 실행하고 싶을 때 CommandLineRunner 인터페이스를 구현한 Bean을 사용합니다.
	 * 여기서는 애플리케이션 시작 시 초기 코인 데이터를 데이터베이스에 저장하는 코드를 실행합니다.
	 *
	 * @param coinService Spring이 자동으로 주입해주는 CoinService 객체입니다.
	 * @return CommandLineRunner 인터페이스의 구현체(람다 표현식)를 Spring Bean으로 등록합니다.
	 */
	@Bean // @Bean: 이 메서드가 반환하는 객체(여기서는 CommandLineRunner 구현체)를 Spring 컨테이너에 Bean으로 등록합니다.
	public CommandLineRunner initDatabase(CoinService coinService) {
		// 람다 표현식을 사용하여 CommandLineRunner 인터페이스의 run 메서드를 구현합니다.
		// 애플리케이션 시작 시 이 람다 코드가 실행됩니다.
		return args -> {
			log.info("Initializing database with sample coin data..."); // 데이터 초기화 시작 로그
			// 주입받은 coinService 객체의 saveInitialCoins 메서드를 호출하여 초기 데이터를 저장합니다.
			coinService.saveInitialCoins();
			log.info("Database initialization finished."); // 데이터 초기화 완료 로그

			// TODO: 필요하다면 애플리케이션 시작 시 다른 초기화 작업 코드를 여기에 추가할 수 있습니다.
		};
	}
}
