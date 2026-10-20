package com.umss.model;

import java.util.List;

public record ResultadoPromedio(List<ResumenGrupo> grupos, int n, int sinCodificar,
                                double suma, Double mediaGlobal) {}