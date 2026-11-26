package com.viajajunto.api.modules.activity.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.viajajunto.api.core.exception.BusinessRuleException;
import com.viajajunto.api.core.exception.ResourceNotFoundException;
import com.viajajunto.api.core.exception.ScheduleConflictException;
import com.viajajunto.api.core.security.TripSecurityService;
import com.viajajunto.api.modules.activity.dto.AtividadeResponseDTO;
import com.viajajunto.api.modules.activity.dto.CreateAtividadeDTO;
import com.viajajunto.api.modules.activity.dto.UpdateAtividadeDTO;
import com.viajajunto.api.modules.activity.entity.AtividadeViagemEntity;
import com.viajajunto.api.modules.activity.entity.CatalogoAtividadeEntity;
import com.viajajunto.api.modules.activity.entity.StatusAtividade;
import com.viajajunto.api.modules.activity.repository.AtividadeViagemRepository;
import com.viajajunto.api.modules.activity.repository.CatalogoAtividadeRepository;
import com.viajajunto.api.modules.destination.entity.DestinoViagemEntity;
import com.viajajunto.api.modules.destination.repository.DestinoViagemRepository;
import com.viajajunto.api.modules.trip.entity.ViagemEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class AtividadeViagemServiceTest {

    @Mock
    private AtividadeViagemRepository atividadeViagemRepository;

    @Mock
    private DestinoViagemRepository destinoViagemRepository;

    @Mock
    private CatalogoAtividadeRepository catalogoAtividadeRepository;

    @Mock
    private TripSecurityService tripSecurityService;

    @InjectMocks
    private AtividadeViagemService atividadeViagemService;

    private ViagemEntity sampleViagem;
    private DestinoViagemEntity sampleDestino;

    @BeforeEach
    void setUp() {
        sampleViagem = ViagemEntity.builder()
                .id(1L)
                .nome("Viagem Teste")
                .build();

        sampleDestino = DestinoViagemEntity.builder()
                .id(10L)
                .nome("Destino Teste")
                .viagem(sampleViagem)
                .build();
    }

    @Test
    @DisplayName("Deve adicionar atividade com sucesso quando não há conflito de horário")
    void addAtividade_Success() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 10, 0);
        CreateAtividadeDTO dto = CreateAtividadeDTO.builder()
                .nome("Passeio de Barco")
                .dataHorario(start)
                .duracaoMinutos(60)
                .build();

        when(destinoViagemRepository.findById(10L)).thenReturn(Optional.of(sampleDestino));
        when(atividadeViagemRepository.findAllByViagemId(1L)).thenReturn(List.of());
        when(atividadeViagemRepository.save(any(AtividadeViagemEntity.class))).thenAnswer(i -> {
            AtividadeViagemEntity a = i.getArgument(0);
            a.setId(100L);
            return a;
        });

        AtividadeResponseDTO response = atividadeViagemService.addAtividade(1L, 10L, dto, 1L);

        assertNotNull(response);
        assertEquals("Passeio de Barco", response.getNome());
        verify(atividadeViagemRepository).save(any(AtividadeViagemEntity.class));
    }

    @Test
    @DisplayName("Deve usar valores do catálogo como fallback quando campos do DTO forem nulos")
    void addAtividade_FallbackToCatalog_WhenFieldsAreNull() {
        CatalogoAtividadeEntity catalogo = CatalogoAtividadeEntity.builder()
                .id(200L)
                .nome("Torre Eiffel")
                .tipo("Passeio")
                .localizacao("Paris")
                .fotoUrl("https://foto.com/torre.jpg")
                .descricao("Subida na torre")
                .build();

        CreateAtividadeDTO dto = CreateAtividadeDTO.builder()
                .nome("Visita Torre")
                .catalogoAtividadeId(200L)
                .build();

        when(destinoViagemRepository.findById(10L)).thenReturn(Optional.of(sampleDestino));
        when(catalogoAtividadeRepository.findById(200L)).thenReturn(Optional.of(catalogo));
        when(atividadeViagemRepository.save(any(AtividadeViagemEntity.class))).thenAnswer(i -> {
            AtividadeViagemEntity a = i.getArgument(0);
            a.setId(101L);
            return a;
        });

        AtividadeResponseDTO response = atividadeViagemService.addAtividade(1L, 10L, dto, 1L);

        assertNotNull(response);
        assertEquals("Visita Torre", response.getNome());
        assertEquals("Passeio", response.getTipo());
        assertEquals("Paris", response.getLocal());
        assertEquals("https://foto.com/torre.jpg", response.getFotoUrl());
        assertEquals("Subida na torre", response.getDescricao());
    }

    @Test
    @DisplayName("Deve lançar ScheduleConflictException quando horários de atividades se sobrepõem")
    void addAtividade_ThrowsScheduleConflictException() {
        LocalDateTime existingStart = LocalDateTime.of(2026, 11, 1, 10, 0);
        AtividadeViagemEntity existing = AtividadeViagemEntity.builder()
                .id(50L)
                .nome("Tour Museu")
                .dataHorario(existingStart)
                .duracaoMinutos(90) // 10:00 às 11:30
                .build();

        LocalDateTime newStart = LocalDateTime.of(2026, 11, 1, 11, 0); // 11:00 às 12:00 -> conflito!
        CreateAtividadeDTO dto = CreateAtividadeDTO.builder()
                .nome("Almoço Especial")
                .dataHorario(newStart)
                .duracaoMinutos(60)
                .build();

        when(destinoViagemRepository.findById(10L)).thenReturn(Optional.of(sampleDestino));
        when(atividadeViagemRepository.findAllByViagemId(1L)).thenReturn(List.of(existing));

        assertThrows(ScheduleConflictException.class, () ->
                atividadeViagemService.addAtividade(1L, 10L, dto, 1L)
        );

        verify(atividadeViagemRepository, never()).save(any(AtividadeViagemEntity.class));
    }

    @Test
    @DisplayName("Deve lançar ScheduleConflictException para atividades sem duração no mesmo horário")
    void addAtividade_ThrowsScheduleConflictException_WhenSameStartWithoutDuration() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 10, 0);
        AtividadeViagemEntity existing = AtividadeViagemEntity.builder()
                .id(50L)
                .nome("Check-in")
                .dataHorario(start)
                .build();

        CreateAtividadeDTO dto = CreateAtividadeDTO.builder()
                .nome("Retirar chaves")
                .dataHorario(start)
                .build();

        when(destinoViagemRepository.findById(10L)).thenReturn(Optional.of(sampleDestino));
        when(atividadeViagemRepository.findAllByViagemId(1L)).thenReturn(List.of(existing));

        assertThrows(ScheduleConflictException.class, () ->
                atividadeViagemService.addAtividade(1L, 10L, dto, 1L)
        );

        verify(atividadeViagemRepository, never()).save(any(AtividadeViagemEntity.class));
    }

    private AtividadeViagemEntity savedActivity() {
        return AtividadeViagemEntity.builder().id(50L).nome("Tour").destinoViagem(sampleDestino).build();
    }

    @Test
    @DisplayName("Remove a atividade pela rota da viagem, que não informa o destino (destinoId nulo)")
    void removeAtividade_WithoutDestinationId_DeletesWhenActivityBelongsToTrip() {
        AtividadeViagemEntity atividade = savedActivity();
        when(atividadeViagemRepository.findById(50L)).thenReturn(Optional.of(atividade));

        atividadeViagemService.removeAtividade(1L, null, 50L, 1L);

        verify(tripSecurityService).validateUserCanEditTrip(1L, 1L);
        verify(atividadeViagemRepository).delete(atividade);
    }

    @Test
    @DisplayName("Remove a atividade quando o destino informado é o dela")
    void removeAtividade_WithMatchingDestination_Deletes() {
        AtividadeViagemEntity atividade = savedActivity();
        when(atividadeViagemRepository.findById(50L)).thenReturn(Optional.of(atividade));

        atividadeViagemService.removeAtividade(1L, 10L, 50L, 1L);

        verify(atividadeViagemRepository).delete(atividade);
    }

    @Test
    @DisplayName("Não remove quando o destino informado não é o da atividade")
    void removeAtividade_ThrowsWhenActivityBelongsToAnotherDestination() {
        when(atividadeViagemRepository.findById(50L)).thenReturn(Optional.of(savedActivity()));

        assertThrows(BusinessRuleException.class, () ->
                atividadeViagemService.removeAtividade(1L, 99L, 50L, 1L)
        );

        verify(atividadeViagemRepository, never()).delete(any(AtividadeViagemEntity.class));
    }

    @Test
    @DisplayName("Não remove quando a atividade pertence a outra viagem")
    void removeAtividade_ThrowsWhenActivityBelongsToAnotherTrip() {
        when(atividadeViagemRepository.findById(50L)).thenReturn(Optional.of(savedActivity()));

        assertThrows(BusinessRuleException.class, () ->
                atividadeViagemService.removeAtividade(2L, null, 50L, 1L)
        );

        verify(atividadeViagemRepository, never()).delete(any(AtividadeViagemEntity.class));
    }

    @Test
    @DisplayName("Atividade inexistente responde ResourceNotFoundException")
    void removeAtividade_ThrowsWhenActivityDoesNotExist() {
        when(atividadeViagemRepository.findById(50L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                atividadeViagemService.removeAtividade(1L, null, 50L, 1L)
        );

        verify(atividadeViagemRepository, never()).delete(any(AtividadeViagemEntity.class));
    }

    // ------------------------------------------------------------ atualização (PATCH)

    private AtividadeViagemEntity activity(Long id, String nome, String tipo, LocalDateTime inicio, Integer duracao) {
        return AtividadeViagemEntity.builder()
                .id(id).nome(nome).tipo(tipo).dataHorario(inicio).duracaoMinutos(duracao)
                .descricao("Antiga").local("Centro").custoPrevisto(BigDecimal.TEN).status(StatusAtividade.PENDENTE)
                .destinoViagem(sampleDestino)
                .build();
    }

    @Test
    @DisplayName("PATCH muda só os campos enviados e mantém o resto")
    void updateAtividade_ChangesOnlySentFields() {
        AtividadeViagemEntity atual = activity(50L, "Tour", "Passeio", LocalDateTime.of(2026, 11, 1, 10, 0), 60);
        when(atividadeViagemRepository.findById(50L)).thenReturn(Optional.of(atual));
        when(atividadeViagemRepository.save(any(AtividadeViagemEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateAtividadeDTO dto = UpdateAtividadeDTO.builder().status(StatusAtividade.CONFIRMADA).descricao("Reserva 123").build();
        AtividadeResponseDTO response = atividadeViagemService.updateAtividade(1L, 50L, dto, 1L);

        assertEquals(StatusAtividade.CONFIRMADA, response.getStatus());
        assertEquals("Reserva 123", response.getDescricao());
        assertEquals("Tour", response.getNome());
        assertEquals(60, response.getDuracaoMinutos());
        assertEquals("Centro", response.getLocal());
        // Sem mudança de horário, não consulta as outras atividades.
        verify(atividadeViagemRepository, never()).findAllByViagemId(any());
        verify(tripSecurityService).validateUserCanEditTrip(1L, 1L);
    }

    @Test
    @DisplayName("PATCH com string vazia apaga local e descrição")
    void updateAtividade_BlankClearsText() {
        when(atividadeViagemRepository.findById(50L)).thenReturn(Optional.of(activity(50L, "Tour", "Passeio", null, null)));
        when(atividadeViagemRepository.save(any(AtividadeViagemEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AtividadeResponseDTO response = atividadeViagemService.updateAtividade(1L, 50L, UpdateAtividadeDTO.builder().local(" ").descricao("").build(), 1L);

        assertNull(response.getLocal());
        assertNull(response.getDescricao());
    }

    @Test
    @DisplayName("PATCH de atividade de outra viagem responde ResourceNotFoundException")
    void updateAtividade_ThrowsWhenActivityBelongsToAnotherTrip() {
        when(atividadeViagemRepository.findById(50L)).thenReturn(Optional.of(activity(50L, "Tour", null, null, null)));

        assertThrows(ResourceNotFoundException.class, () ->
                atividadeViagemService.updateAtividade(2L, 50L, UpdateAtividadeDTO.builder().nome("X").build(), 1L));

        verify(atividadeViagemRepository, never()).save(any(AtividadeViagemEntity.class));
    }

    @Test
    @DisplayName("PATCH que move o horário para cima de outra atividade responde ScheduleConflictException")
    void updateAtividade_ThrowsOnScheduleConflict() {
        AtividadeViagemEntity atual = activity(50L, "Tour", "Passeio", LocalDateTime.of(2026, 11, 1, 8, 0), 60);
        AtividadeViagemEntity outra = activity(51L, "Museu", "Cultura", LocalDateTime.of(2026, 11, 1, 10, 0), 90);
        when(atividadeViagemRepository.findById(50L)).thenReturn(Optional.of(atual));
        when(atividadeViagemRepository.findAllByViagemId(1L)).thenReturn(List.of(atual, outra));

        UpdateAtividadeDTO dto = UpdateAtividadeDTO.builder().dataHorario(LocalDateTime.of(2026, 11, 1, 10, 30)).build();
        assertThrows(ScheduleConflictException.class, () -> atividadeViagemService.updateAtividade(1L, 50L, dto, 1L));

        verify(atividadeViagemRepository, never()).save(any(AtividadeViagemEntity.class));
    }

    @Test
    @DisplayName("PATCH não conflita com a própria atividade (mudar só a duração)")
    void updateAtividade_IgnoresItselfWhenCheckingConflicts() {
        AtividadeViagemEntity atual = activity(50L, "Tour", "Passeio", LocalDateTime.of(2026, 11, 1, 10, 0), 60);
        when(atividadeViagemRepository.findById(50L)).thenReturn(Optional.of(atual));
        when(atividadeViagemRepository.findAllByViagemId(1L)).thenReturn(List.of(atual));
        when(atividadeViagemRepository.save(any(AtividadeViagemEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AtividadeResponseDTO response = atividadeViagemService.updateAtividade(1L, 50L, UpdateAtividadeDTO.builder().duracaoMinutos(120).build(), 1L);

        assertEquals(120, response.getDuracaoMinutos());
    }

    @Test
    @DisplayName("Hospedagem não conflita: dá para marcar um passeio durante a estadia")
    void addAtividade_LodgingDoesNotBlockActivitiesDuringTheStay() {
        AtividadeViagemEntity hotel = activity(60L, "Hotel", "Hospedagem", LocalDateTime.of(2026, 11, 1, 14, 0), 3 * 1440);
        when(destinoViagemRepository.findById(10L)).thenReturn(Optional.of(sampleDestino));
        when(atividadeViagemRepository.findAllByViagemId(1L)).thenReturn(List.of(hotel));
        when(atividadeViagemRepository.save(any(AtividadeViagemEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateAtividadeDTO dto = CreateAtividadeDTO.builder().nome("Passeio").dataHorario(LocalDateTime.of(2026, 11, 2, 10, 0)).duracaoMinutos(120).build();

        assertDoesNotThrow(() -> atividadeViagemService.addAtividade(1L, 10L, dto, 1L));
    }
}
