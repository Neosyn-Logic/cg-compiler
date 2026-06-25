/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - Network compositions for regression testing.
 * Tests: task instantiation, port connections, inline tasks.
 */

/**
 * Simple consumer task - reads and accumulates values.
 */
module Consumer(input clock, input reset_n, input [7 : 0] data, output reg [15 : 0] sum);


  /**
   * State variables
   */
  reg [15 : 0] total;
  
  
  
  
  
  /**
   * Synchronous process
   */
  always @(negedge reset_n or posedge clock) begin // body of Consumer
    if (~reset_n) begin
      total <= 16'h0;
      sum <= 16'b0;
    end else begin
      
      begin : FSM_Consumer_a // line 33
        reg [16 : 0] orion;
        reg [16 : 0] lyra;
      
        orion = (total + data);
        sum <= orion[15 : 0];
        lyra = (total + data);
        total <= lyra[15 : 0];
      end
    end
  end

endmodule //Consumer
