/*
 * Copyright (c) 2012-2026 Neosyn (neosyn.io). All rights reserved.
 * Not licensed under the MPL-2.0.
 *
 * Free to use, including commercially, as part of hardware designs generated
 * by the C⏚ compiler, whether the open-source compiler or the commercial
 * distribution. You may not extract this file for use on its own,
 * reverse-engineer it, or redistribute it except as part of such a design or
 * of the C⏚ compiler.
 *
 * Provided "AS IS", without warranty of any kind.
 */
module MuxDDR # ( parameter width = 1 ) ( input clock , input rising_valid , input [ width - 1 : 0 ] rising , input falling_valid , input [ width - 1 : 0 ] falling , output dout_valid , output [ width - 1 : 0 ] dout ) ;
assign dout = clock ? rising : falling ;
assign dout_valid = clock ? rising_valid : falling_valid ;
endmodule
