package com.viajajunto.api.modules.activity.dto;

import com.viajajunto.api.modules.activity.entity.StatusAtividade;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PATCH /trips/{viagemId}/activities/{atividadeId}: atualização parcial. Campo ausente (null) não muda.
 * Para apagar o texto de {@code local} ou {@code descricao}, envie uma string vazia.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAtividadeDTO {

    // null passa (campo não enviado); preenchido, precisa ter ao menos um caractere não branco.
    // "\\s*+" é possessivo: não volta atrás, então a validação é linear mesmo com muitos espaços.
    @Pattern(regexp = "\\s*+\\S.*", message = "O nome da atividade não pode ficar em branco.")
    @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres.")
    private String nome;

    @Size(max = 255, message = "O tipo deve ter no máximo 255 caracteres.")
    private String tipo;

    @Size(max = 255, message = "O local deve ter no máximo 255 caracteres.")
    private String local;

    @Size(max = 1000, message = "A descrição pode ter no máximo 1000 caracteres.")
    private String descricao;

    private LocalDateTime dataHorario;

    @Min(value = 0, message = "A duração não pode ser negativa.")
    private Integer duracaoMinutos;

    @DecimalMin(value = "0.0", inclusive = true, message = "O custo previsto não pode ser negativo.")
    private BigDecimal custoPrevisto;

    private StatusAtividade status;
}
