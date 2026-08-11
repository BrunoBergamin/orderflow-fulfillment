package br.com.bergamin.fulfillment.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * As mesmas regras do servico de pedidos, aplicadas aqui.
 *
 * <p>Manter a disciplina igual nos dois servicos e o que permite trocar de contexto sem
 * reaprender onde as coisas ficam.</p>
 */
@AnalyzeClasses(
        packages = "br.com.bergamin.fulfillment",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMINIO = "..domain..";
    private static final String APLICACAO = "..application..";
    private static final String INFRAESTRUTURA = "..infrastructure..";

    @ArchTest
    static final ArchRule dependencias_apontam_para_dentro = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Dominio").definedBy(DOMINIO)
            .layer("Aplicacao").definedBy(APLICACAO)
            .layer("Infraestrutura").definedBy(INFRAESTRUTURA)
            .whereLayer("Infraestrutura").mayNotBeAccessedByAnyLayer()
            .whereLayer("Aplicacao").mayOnlyBeAccessedByLayers("Infraestrutura")
            .whereLayer("Dominio").mayOnlyBeAccessedByLayers("Aplicacao", "Infraestrutura");

    @ArchTest
    static final ArchRule dominio_nao_conhece_framework = noClasses()
            .that().resideInAPackage(DOMINIO)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta..",
                    "org.hibernate..",
                    "com.fasterxml..",
                    "org.apache.kafka..",
                    "io.github.resilience4j..")
            .because("a politica de retentativa e as regras da projecao precisam ser testaveis sem container");

    @ArchTest
    static final ArchRule aplicacao_nao_conhece_infraestrutura = noClasses()
            .that().resideInAPackage(APLICACAO)
            .should().dependOnClassesThat().resideInAPackage(INFRAESTRUTURA);

    /**
     * Kafka, Redis e Resilience4j sao detalhes de entrega.
     *
     * <p>Se um deles vazar para os casos de uso, trocar de broker ou de cache vira
     * reescrita de regra de negocio.</p>
     */
    @ArchTest
    static final ArchRule tecnologias_ficam_na_borda = noClasses()
            .that().resideInAnyPackage(DOMINIO, APLICACAO)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.apache.kafka..",
                    "org.springframework.kafka..",
                    "org.springframework.data.redis..",
                    "io.github.resilience4j..");

    @ArchTest
    static final ArchRule portas_sao_interfaces = classes()
            .that().resideInAnyPackage("..application.port.in..", "..application.port.out..")
            .and().areTopLevelClasses()
            .should().beInterfaces();

    @ArchTest
    static final ArchRule entidades_jpa_so_na_infraestrutura = noClasses()
            .that().resideOutsideOfPackage(INFRAESTRUTURA)
            .should().beAnnotatedWith(Entity.class);

    @ArchTest
    static final ArchRule controllers_nao_acessam_repositorios = noClasses()
            .that().resideInAPackage("..adapter.in.rest..")
            .should().dependOnClassesThat().resideInAPackage("..persistence..");

    @ArchTest
    static final ArchRule sem_dependencias_circulares = slices()
            .matching("br.com.bergamin.fulfillment.(*)..")
            .should().beFreeOfCycles();
}
