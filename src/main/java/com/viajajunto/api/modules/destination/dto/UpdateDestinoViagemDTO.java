package com.viajajunto.api.modules.destination.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * PATCH /trips/{viagemId}/destinations/{destinoId}: atualização parcial. Campo ausente (null) não muda.
 * Para apagar um texto opcional (país, código, localização, descrição), envie uma string vazia.
 * {@code ordemVisita} move o destino para essa posição do roteiro e renumera os outros.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDestinoViagemDTO {

    // null passa (campo não enviado); preenchido, precisa ter ao menos um caractere não branco.
    // "\\s*+" é possessivo: não volta atrás, então a validação é linear mesmo com muitos espaços.
    @Pattern(regexp = "\\s*+\\S.*", message = "O nome do destino não pode ficar em branco.")
    @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres.")
    private String nome;

    @Size(max = 255, message = "O país deve ter no máximo 255 caracteres.")
    private String pais;

    @Size(max = 3, message = "O código do país tem no máximo 3 letras (ISO 3166).")
    private String codigoPaisIso;

    @Size(max = 255, message = "A localização deve ter no máximo 255 caracteres.")
    private String localizacao;

    @Size(max = 1000, message = "A descrição pode ter no máximo 1000 caracteres.")
    private String descricao;

    private LocalDate dataChegada;

    private LocalDate dataSaida;

    @Min(value = 1, message = "A ordem de visita começa em 1.")
    private Integer ordemVisita;
}
