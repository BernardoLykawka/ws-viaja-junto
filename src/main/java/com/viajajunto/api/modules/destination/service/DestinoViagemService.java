package com.viajajunto.api.modules.destination.service;

import static com.viajajunto.api.core.util.FallbackUtils.resolveValue;

import com.viajajunto.api.core.exception.BusinessRuleException;
import com.viajajunto.api.core.exception.ResourceNotFoundException;
import com.viajajunto.api.core.security.TripSecurityService;
import com.viajajunto.api.modules.destination.dto.CreateDestinoViagemDTO;
import com.viajajunto.api.modules.destination.dto.UpdateDestinoViagemDTO;
import com.viajajunto.api.modules.destination.dto.DestinoViagemResponseDTO;
import com.viajajunto.api.modules.destination.entity.DestinoCatalogoEntity;
import com.viajajunto.api.modules.destination.entity.DestinoViagemEntity;
import com.viajajunto.api.modules.destination.repository.DestinoCatalogoRepository;
import com.viajajunto.api.modules.destination.repository.DestinoViagemRepository;
import com.viajajunto.api.modules.trip.entity.ViagemEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DestinoViagemService {

    private final DestinoViagemRepository destinoViagemRepository;
    private final DestinoCatalogoRepository destinoCatalogoRepository;
    private final TripSecurityService tripSecurityService;

    @Transactional
    public DestinoViagemResponseDTO addDestinoToViagem(Long viagemId, CreateDestinoViagemDTO dto, Long userId) {
        ViagemEntity viagem = tripSecurityService.validateUserCanEditTrip(viagemId, userId);

        DestinoCatalogoEntity catalogo = null;
        if (dto.getDestinoCatalogoId() != null) {
            catalogo = destinoCatalogoRepository.findById(dto.getDestinoCatalogoId()).orElse(null);
        }

        List<DestinoViagemEntity> existing = destinoViagemRepository.findAllByViagemIdOrderByOrdemVisitaAsc(viagemId);
        int nextOrder = dto.getOrdemVisita() != null ? dto.getOrdemVisita() : existing.size() + 1;

        DestinoViagemEntity destino = DestinoViagemEntity.builder()
                .viagem(viagem)
                .destinoCatalogo(catalogo)
                .nome(dto.getNome())
                .pais(resolveValue(dto.getPais(), catalogo, DestinoCatalogoEntity::getPais))
                .codigoPaisIso(resolveValue(dto.getCodigoPaisIso(), catalogo, DestinoCatalogoEntity::getCodigoPaisIso))
                .localizacao(resolveValue(dto.getLocalizacao(), catalogo, DestinoCatalogoEntity::getCidade))
                .fotoUrl(resolveValue(dto.getFotoUrl(), catalogo, DestinoCatalogoEntity::getFotoUrl))
                .descricao(resolveValue(dto.getDescricao(), catalogo, DestinoCatalogoEntity::getDescricao))
                .categoria(resolveValue(dto.getCategoria(), catalogo, DestinoCatalogoEntity::getCategoria))
                .dataChegada(dto.getDataChegada())
                .dataSaida(dto.getDataSaida())
                .ordemVisita(nextOrder)
                .build();

        DestinoViagemEntity saved = destinoViagemRepository.save(destino);
        return mapToDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<DestinoViagemResponseDTO> listDestinosByViagem(Long viagemId, Long userId) {
        tripSecurityService.validateUserCanViewTrip(viagemId, userId);
        return destinoViagemRepository.findAllByViagemIdOrderByOrdemVisitaAsc(viagemId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void removeDestino(Long viagemId, Long destinoId, Long userId) {
        tripSecurityService.validateUserCanEditTrip(viagemId, userId);

        DestinoViagemEntity destino = destinoViagemRepository.findById(destinoId)
                .orElseThrow(() -> new ResourceNotFoundException("Destino não encontrado na viagem com ID: " + destinoId));

        if (!destino.getViagem().getId().equals(viagemId)) {
            throw new BusinessRuleException("O destino não pertence a esta viagem.");
        }

        destinoViagemRepository.delete(destino);
    }

    /**
     * Atualização parcial (PATCH): só os campos enviados mudam. A saída não pode ficar antes da chegada
     * (contando com as datas que já estavam salvas). Mudar {@code ordemVisita} reposiciona o destino no
     * roteiro e renumera os outros de 1 em diante, sem buracos nem repetições.
     */
    @Transactional
    public DestinoViagemResponseDTO updateDestino(Long viagemId, Long destinoId, UpdateDestinoViagemDTO dto, Long userId) {
        tripSecurityService.validateUserCanEditTrip(viagemId, userId);

        DestinoViagemEntity destino = destinoViagemRepository.findById(destinoId)
                .filter(d -> d.getViagem().getId().equals(viagemId))
                .orElseThrow(() -> new ResourceNotFoundException("Destino não encontrado nesta viagem com ID: " + destinoId));

        LocalDate chegada = dto.getDataChegada() != null ? dto.getDataChegada() : destino.getDataChegada();
        LocalDate saida = dto.getDataSaida() != null ? dto.getDataSaida() : destino.getDataSaida();
        if (chegada != null && saida != null && saida.isBefore(chegada)) {
            throw new BusinessRuleException("A data de saída não pode ser antes da chegada.");
        }

        if (dto.getNome() != null) {
            destino.setNome(dto.getNome().trim());
        }
        if (dto.getPais() != null) {
            destino.setPais(blankToNull(dto.getPais()));
        }
        if (dto.getCodigoPaisIso() != null) {
            String iso = blankToNull(dto.getCodigoPaisIso());
            destino.setCodigoPaisIso(iso != null ? iso.toUpperCase(Locale.ROOT) : null);
        }
        if (dto.getLocalizacao() != null) {
            destino.setLocalizacao(blankToNull(dto.getLocalizacao()));
        }
        if (dto.getDescricao() != null) {
            destino.setDescricao(blankToNull(dto.getDescricao()));
        }
        if (dto.getDataChegada() != null) {
            destino.setDataChegada(dto.getDataChegada());
        }
        if (dto.getDataSaida() != null) {
            destino.setDataSaida(dto.getDataSaida());
        }
        if (dto.getOrdemVisita() != null) {
            reordenar(viagemId, destino, dto.getOrdemVisita());
        }

        return mapToDTO(destinoViagemRepository.save(destino));
    }

    /** Coloca o destino na posição pedida (limitada ao tamanho do roteiro) e renumera todos de 1 a N. */
    private void reordenar(Long viagemId, DestinoViagemEntity destino, int posicao) {
        List<DestinoViagemEntity> roteiro = new ArrayList<>(destinoViagemRepository.findAllByViagemIdOrderByOrdemVisitaAsc(viagemId));
        roteiro.removeIf(d -> d.getId().equals(destino.getId()));
        int indice = Math.max(0, Math.min(posicao - 1, roteiro.size()));
        roteiro.add(indice, destino);
        for (int i = 0; i < roteiro.size(); i++) {
            roteiro.get(i).setOrdemVisita(i + 1);
        }
        destinoViagemRepository.saveAll(roteiro);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private DestinoViagemResponseDTO mapToDTO(DestinoViagemEntity entity) {
        return DestinoViagemResponseDTO.builder()
                .id(entity.getId())
                .viagemId(entity.getViagem().getId())
                .destinoCatalogoId(entity.getDestinoCatalogo() != null ? entity.getDestinoCatalogo().getId() : null)
                .nome(entity.getNome())
                .pais(entity.getPais())
                .codigoPaisIso(entity.getCodigoPaisIso())
                .localizacao(entity.getLocalizacao())
                .fotoUrl(entity.getFotoUrl())
                .descricao(entity.getDescricao())
                .categoria(entity.getCategoria())
                .dataChegada(entity.getDataChegada())
                .dataSaida(entity.getDataSaida())
                .ordemVisita(entity.getOrdemVisita())
                .build();
    }
}
