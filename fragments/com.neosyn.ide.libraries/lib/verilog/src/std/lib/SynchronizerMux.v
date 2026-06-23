/*
 * Copyright (c) 2013-2021, Neosyn
 * All rights reserved.
 * 
 * REDISTRIBUTION of this file in source and binary forms, with or without
 * modification, is NOT permitted in any way.
 *
 * The use of this file in source and binary forms, with or without
 * modification, is permitted if you have a valid commercial license of
 * Neosyn IDE.
 * If you do NOT have a valid license of Neosyn IDE: you are NOT allowed
 * to use this file.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE
 */

/**
 * Title   : Mux synchronizer
 * Authors : Neosyn team
 */
module SynchronizerMux
  #(parameter width = 32, parameter stages = 2)
  (
    input reset_n,
    input din_clock,
    input dout_clock,
    input din_valid,
    input [width - 1 : 0] din,
    output reg [width - 1 : 0] dout
  );

  /**
   * internal signals
   */
  wire control_sync;
  
  SynchronizerFF #(
    .stages(stages)
  )
  sync(
    .reset_n(reset_n),
    .din_clock(din_clock),
    .dout_clock(dout_clock),
    .din(din_valid),
    .dout(control_sync)
  );

  always @(negedge reset_n or posedge dout_clock)
    if (~reset_n) begin
      dout <= 0;
    end else begin
      if (control_sync)
        dout <= din;
    end

endmodule
