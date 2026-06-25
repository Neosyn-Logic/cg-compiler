/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - Combinational logic for regression testing.
 * Tests: properties { type: "combinational" }, pure logic without state.
 */

/**
 * Multiplexer - 4-to-1 mux.
 * Tests: selection logic.
 */
module Mux4to1(input [7 : 0] d0, input [7 : 0] d1, input [7 : 0] d2, input [7 : 0] d3, input [1 : 0] sel, output reg [7 : 0] y);


  /**
   * State variables
   */
  
  
  
  
  /**
   * Combinational process
   */
  always @(d0 or d1 or d2 or d3 or sel) begin
    y = 8'b0;
    begin // line 66
      if ((sel == 2'h0)) begin
        y = d0;
      end else begin
        if ((sel == 2'h1)) begin
          y = d1;
        end else begin
          if ((sel == 2'h2)) begin
            y = d2;
          end else begin
            y = d3;
          end
        end
      end
    end
  end
  

endmodule //Mux4to1
