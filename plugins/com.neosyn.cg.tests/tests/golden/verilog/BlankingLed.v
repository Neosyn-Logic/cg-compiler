/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - Basic tasks for regression testing.
 * Tests: simple state variables, arithmetic, output ports.
 */

/**
 * Minimal toggle task - tests boolean state and output.
 * Based on: LED/BlankingLed.cg
 */
module BlankingLed(input clock, input reset_n, output reg  led);


  /**
   * State variables
   */
  reg  state;
  
  
  
  
  
  /**
   * Synchronous process
   */
  always @(negedge reset_n or posedge clock) begin // body of BlankingLed
    if (~reset_n) begin
      state <= 1'b0;
      led <= 1'b0;
    end else begin
      
      begin // line 19
        led <= state;
        state <= ! (state);
      end
    end
  end

endmodule //BlankingLed
