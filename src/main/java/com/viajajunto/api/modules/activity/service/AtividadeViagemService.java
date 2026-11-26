package com.viajajunto.api.modules.activity.service;

import static com.viajajunto.api.core.util.FallbackUtils.resolveValue;

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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AtividadeViagemService {

    private final AtividadeViagemRepository atividadeViagemRepository;
    private final DestinoViagemRepository destinoViagemRepository;
    private final CatalogoAtividadeRepository catalogoAtividadeRepository;
    private final TripSecurityService tripSecurityService;

    @Transactional
    public AtividadeResponseDTO addAtividade(Long viagemId, Long destinoId, CreateAtividadeDTO dto, Long userId) {
        tripSecurityService.validateUserCanEditTrip(viagemId, userId);

        DestinoViagemEntity destino = destinoViagemRepository.findById(destinoId)
                .orElseThrow(() -> new ResourceNotFoundException("Destino não encontrado na viagem com ID: " + destinoId));

        if (!destino.getViagem().getId().equals(viagemId)) {
            throw new BusinessRuleException("O destino não pertence à viagem indicada.");
        }

        validarHorarioLivre(viagemId, null, dto.getDataHorario(), dto.getDuracaoMinutos(), dto.getTipo());

        CatalogoAtividadeEntity catalogo = null;
        if (dto.getCatalogoAtividadeId() != null) {
            catalogo = catalogoAtividadeRepository.findById(dto.getCatalogoAtividadeId()).orElse(null);
        }

        AtividadeViagemEntity atividade = AtividadeViagemEntity.builder()
                .destinoViagem(destino)
                .catalogoAtividade(catalogo)
                .nome(dto.getNome())
                .tipo(resolveValue(dto.getTipo(), catalogo, CatalogoAtividadeEntity::getTipo))
                .local(resolveValue(dto.getLocal(), catalogo, CatalogoAtividadeEntity::getLocalizacao))
                .fotoUrl(resolveValue(dto.getFotoUrl(), catalogo, CatalogoAtividadeEntity::getFotoUrl))
                .descricao(resolveValue(dto.getDescricao(), catalogo, CatalogoAtividadeEntity::getDescricao))
                .dataHorario(dto.getDataHorario())
                .duracaoMinutos(dto.getDuracaoMinutos())
                .custoPrevisto(dto.getCustoPrevisto() != null ? dto.getCustoPrevisto() : BigDecimal.ZERO)
                .status(dto.getStatus() != null ? dto.getStatus() : StatusAtividade.PENDENTE)
                .build();

        AtividadeViagemEntity saved = atividadeViagemRepository.save(atividade);
        return mapToDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<AtividadeResponseDTO> listAtividadesByDestino(Long viagemId, Long destinoId, Long userId) {
        tripSecurityService.validateUserCanViewTrip(viagemId, userId);

        DestinoViagemEntity destino = destinoViagemRepository.findById(destinoId)
                .orElseThrow(() -> new ResourceNotFoundException("Destino não encontrado na viagem com ID: " + destinoId));

        if (!destino.getViagem().getId().equals(viagemId)) {
            throw new BusinessRuleException("O destino não pertence à viagem indicada.");
        }

        return atividadeViagemRepository.findAllByDestinoViagemIdOrderByDataHorarioAsc(destinoId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<AtividadeResponseDTO> listAtividadesByViagem(Long viagemId, Long userId, Pageable pageable) {
        tripSecurityService.validateUserCanViewTrip(viagemId, userId);
        return atividadeViagemRepository.findPageByViagemId(viagemId, pageable)
                .map(this::mapToDTO);
    }

    @Transactional
    public void removeAtividade(Long viagemId, Long destinoId, Long atividadeId, Long userId) {
        tripSecurityService.validateUserCanEditTrip(viagemId, userId);

        AtividadeViagemEntity atividade = atividadeViagemRepository.findById(atividadeId)
                .orElseThrow(() -> new ResourceNotFoundException("Atividade não encontrada com ID: " + atividadeId));

        // destinoId é nulo na rota DELETE /trips/{id}/activities/{id}, que não informa o destino.
        boolean otherDestination = destinoId != null && !atividade.getDestinoViagem().getId().equals(destinoId);
        if (otherDestination || !atividade.getDestinoViagem().getViagem().getId().equals(viagemId)) {
            throw new BusinessRuleException("A atividade não pertence ao destino/viagem especificados.");
        }

        atividadeViagemRepository.delete(atividade);
    }

    /**
     * Atualização parcial (PATCH): só os campos enviados mudam. Se o horário, a duração ou o tipo mudarem,
     * o conflito de horário é checado de novo, ignorando a própria atividade.
     */
    @Transactional
    public AtividadeResponseDTO updateAtividade(Long viagemId, Long atividadeId, UpdateAtividadeDTO dto, Long userId) {
        tripSecurityService.validateUserCanEditTrip(viagemId, userId);

        AtividadeViagemEntity atividade = atividadeViagemRepository.findById(atividadeId)
                .filter(a -> a.getDestinoViagem().getViagem().getId().equals(viagemId))
                .orElseThrow(() -> new ResourceNotFoundException("Atividade não encontrada nesta viagem com ID: " + atividadeId));

        LocalDateTime inicio = dto.getDataHorario() != null ? dto.getDataHorario() : atividade.getDataHorario();
        Integer duracao = dto.getDuracaoMinutos() != null ? dto.getDuracaoMinutos() : atividade.getDuracaoMinutos();
        String tipo = dto.getTipo() != null ? dto.getTipo() : atividade.getTipo();
        boolean mudouAgenda = dto.getDataHorario() != null || dto.getDuracaoMinutos() != null || dto.getTipo() != null;
        if (mudouAgenda) {
            validarHorarioLivre(viagemId, atividadeId, inicio, duracao, tipo);
        }

        if (dto.getNome() != null) {
            atividade.setNome(dto.getNome().trim());
        }
        if (dto.getTipo() != null) {
            atividade.setTipo(blankToNull(dto.getTipo()));
        }
        if (dto.getLocal() != null) {
            atividade.setLocal(blankToNull(dto.getLocal()));
        }
        if (dto.getDescricao() != null) {
            atividade.setDescricao(blankToNull(dto.getDescricao()));
        }
        if (dto.getDataHorario() != null) {
            atividade.setDataHorario(dto.getDataHorario());
        }
        if (dto.getDuracaoMinutos() != null) {
            atividade.setDuracaoMinutos(dto.getDuracaoMinutos());
        }
        if (dto.getCustoPrevisto() != null) {
            atividade.setCustoPrevisto(dto.getCustoPrevisto());
        }
        if (dto.getStatus() != null) {
            atividade.setStatus(dto.getStatus());
        }

        return mapToDTO(atividadeViagemRepository.save(atividade));
    }

    /**
     * Duas atividades com horário não podem se sobrepor (nem começar no mesmo instante, se não têm duração).
     * Hospedagem fica de fora dos dois lados: a estadia dura dias e as atividades acontecem durante ela.
     * {@code ignorarId} é a própria atividade, na atualização.
     */
    private void validarHorarioLivre(Long viagemId, Long ignorarId, LocalDateTime inicio, Integer duracaoMinutos, String tipo) {
        if (inicio == null || isHospedagem(tipo)) {
            return;
        }
        LocalDateTime fim = inicio.plusMinutes(duracaoMinutos != null ? duracaoMinutos : 0);
        for (AtividadeViagemEntity existente : atividadeViagemRepository.findAllByViagemId(viagemId)) {
            if (existente.getDataHorario() == null || existente.getId().equals(ignorarId) || isHospedagem(existente.getTipo())) {
                continue;
            }
            int duracaoExistente = existente.getDuracaoMinutos() != null ? existente.getDuracaoMinutos() : 0;
            LocalDateTime inicioExistente = existente.getDataHorario();
            LocalDateTime fimExistente = inicioExistente.plusMinutes(duracaoExistente);
            boolean sobrepoe = inicio.isBefore(fimExistente) && inicioExistente.isBefore(fim);
            // atividades sem duração no mesmo instante também conflitam
            if (sobrepoe || inicio.isEqual(inicioExistente)) {
                throw new ScheduleConflictException("Conflito de horário com a atividade: " + existente.getNome());
            }
        }
    }

    private static boolean isHospedagem(String tipo) {
        return tipo != null && tipo.trim().equalsIgnoreCase("Hospedagem");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AtividadeResponseDTO mapToDTO(AtividadeViagemEntity entity) {
        return AtividadeResponseDTO.builder()
                .id(entity.getId())
                .destinoViagemId(entity.getDestinoViagem().getId())
                .catalogoAtividadeId(entity.getCatalogoAtividade() != null ? entity.getCatalogoAtividade().getId() : null)
                .nome(entity.getNome())
                .tipo(entity.getTipo())
                .local(entity.getLocal())
                .fotoUrl(entity.getFotoUrl())
                .descricao(entity.getDescricao())
                .dataHorario(entity.getDataHorario())
                .duracaoMinutos(entity.getDuracaoMinutos())
                .custoPrevisto(entity.getCustoPrevisto())
                .status(entity.getStatus())
                .build();
    }
}
